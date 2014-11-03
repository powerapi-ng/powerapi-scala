package org.powerapi.core

import scala.collection._
import scala.concurrent.duration._
import scala.concurrent.Await

import akka.actor.{ Actor, ActorContext, ActorLogging, ActorRef, Cancellable, Props }
import akka.event.{ LoggingReceive }
import akka.pattern.ask

/**
 * One child clock is created per frequency.
 * Allows to publish a message in the right topics for a given frequency.
 */
class ClockChild(frequency: FiniteDuration) extends Actor with ActorLogging with DefaultActorBehavior with ClockChannel {
  import ClockChannel.{ ClockTick, formatTopicFromFrequency, OK, StartClock, StopClock, topic }

  var timer: Option[Cancellable] = None
  val topicsToPublish = formatTopicFromFrequency(frequency)

  def receive = LoggingReceive {
    case msg: StartClock => start(msg.report)
    case unknown => messageUnsupported(unknown)
  }

  def running = LoggingReceive {
    case msg: StopClock => stop()
    case msg: StartClock => operationUnsupported("clock is already running.")
    case unknown => messageUnsupported(unknown)
  }

  /**
   * Starts the clock and the associated scheduler for publishing a Tick on the required topics at a given frequency.
   * 
   * @param report: base message sent on the bus, received by the parent.
   */
  def start(report: Report) = {
    timer = Option(
      context.system.scheduler.schedule(Duration.Zero, frequency) {
        publishOnBus(ClockTick(report.suid, topic, frequency))
      } (context.system.dispatcher)
    )

    if(log.isDebugEnabled) log.debug("clock started, reference: " + frequency.toNanos)
    sender ! OK
    context.become(running)
  }

  /**
   * Stops the clock and the scheduler.
   */
  def stop() = {
    timer match {
      case Some(cancellable) => {
        cancellable.cancel
        if(log.isDebugEnabled) log.debug("clock stopped, reference: " + frequency.toNanos)
        sender ! OK
        context.stop(self)
      }
      case _ => operationUnsupported("unable to stop the scheduler.")
    }
  }
}

/**
 * This clock listens the bus on a given topic and reacts on the received message.
 * It is responsible to handle a pool of clocks for the monitored frequencies.
 */
class Clock extends Actor with ActorLogging with DefaultActorBehavior with ClockChannel {
  import ClockChannel.{ OK, StartClock, StopClock, StopAllClocks }

  implicit val timeout = DefaultTimeout.timeout

  def receive = process(Map.empty[Long, ActorRef])

  override def preStart() = {  
    subscribeOnBus(self)
  }

  def process(buffer: Map[Long, ActorRef]): Actor.Receive = LoggingReceive {
    case msg: StartClock => start(buffer, msg.frequency, msg.report)
    case msg: StopClock => stop(buffer, msg)
    case StopAllClocks => stopAll(buffer)
  }

  /**
   * Starts a new clock at a given frequency whether is needed.
   * 
   * @param buffer: Buffer which contains all references to the clock children.
   * @param frequency: clock frequency.
   */
  def start(buffer: Map[Long, ActorRef], frequency: FiniteDuration, report: Report) = {
    val nanoSecs = frequency.toNanos
    val clock = buffer.getOrElse(nanoSecs, None)

    clock match {
      case None => {
        val props = Props(classOf[ClockChild], frequency)
        val child = context.actorOf(props)
        val ack = Await.result(child ? StartClock(frequency, report), timeout.duration)

        if(ack == OK) {
          context become process(buffer + (nanoSecs -> child))
        }
      }
    }
  }

  /**
   * Stops a clock for a given frequency when is needed.
   * 
   * @param buffer: Buffer which contains all references to the clock children.
   * @param msg: Message received for stopping a clock at a given frequency.
   */
  def stop(buffer: Map[Long, ActorRef], msg: StopClock) = {
    val nanoSecs = msg.frequency.toNanos
    val clock = buffer.getOrElse(nanoSecs, None)

    clock match {
      case ref: ActorRef => {
        val ack = Await.result(ref ? msg, timeout.duration)

        if(ack == OK) {
          context become process(buffer - nanoSecs)
        }
      }
    }
  }

  /**
   * Stop all clocks for all frequencies.
   *
   * @param buffer: Buffer which contains all references to the clock children.
   */
  def stopAll(buffer: Map[Long, ActorRef]) = {
    buffer.foreach({
      case (_, ref) => {
        Await.result(ref ? StopClock(Duration.Zero), timeout.duration)
      }
    })

    context become process(buffer.empty)
  }
}