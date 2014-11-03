package org.powerapi.core

import scala.collection._
import scala.concurrent.duration._
import scala.concurrent.Await

import akka.actor.{ Actor, ActorRef, Cancellable, Props }
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout

/**
 * One child clock is created per frequency.
 * Allows to publish a message in the right topics for a given frequency.
 */
class ClockChild(frequency: FiniteDuration) extends Component with ClockChannel {
  import ClockChannel.{ ClockTick, formatTopicFromFrequency, OK, NOK, StartClock, StopAllClocks, StopClock, topic }

  var timer: Option[Cancellable] = None
  val topicToPublish = formatTopicFromFrequency(frequency)

  def acquire = {
    case msg: StartClock => start(msg.report)
  }

  /**
   * Running state, only one timer per ClockChild. An accumulator is used to know how many subscriptions are using this frequency.
   *
   * @param acc: Accumulator used to know the number of subscriptions which run at this frequency.
   */
  def running(acc: Int): Actor.Receive = {
    case msg: StopClock => stop(acc)
    case msg: StartClock => {
      if(log.isDebugEnabled) log.debug(new StringContext("clock is already started, reference: ").s(frequency.toNanos))
      sender ! NOK
      context.become(running(acc + 1))
    }
    case StopAllClocks => stop(1)
  }

  /**
   * Starts the clock and the associated scheduler for publishing a Tick on the required topics at a given frequency.
   * 
   * @param report: Base message sent on the bus, received by the parent.
   */
  def start(report: Report) = {
    timer = Option(
      context.system.scheduler.schedule(Duration.Zero, frequency) {
        publishOnBus(ClockTick(report.suid, topicToPublish, frequency))
      } (context.system.dispatcher)
    )

    if(log.isDebugEnabled) log.debug(new StringContext("clock started, reference: ", "").s(frequency.toNanos))
    sender ! OK
    context.become(running(1))
  }

  /**
   * Stops the clock and the scheduler.
   *
   * @param acc: Accumulator used to know the number of subscriptions which run at this frequency.
   */
  def stop(acc: Int) = {
    timer match {
      case Some(cancellable) => {
        if(acc > 1) {
          if(log.isDebugEnabled) log.debug(new StringContext("this frequency is still used, clock is still running, reference: ", "").s(frequency.toNanos))
          sender ! NOK
          context.become(running(acc - 1))
        }
        else {
          cancellable.cancel
          timer = None
          if(log.isDebugEnabled) log.debug(new StringContext("clock stopped, reference: ", "").s(frequency.toNanos))
          sender ! OK
          context.stop(self)
        }
      }
      case _ => {
        if(log.isErrorEnabled) log.error(new StringContext("the timer for the clock referenced ", "", " was not initialized.").s(frequency.toNanos))
        sender ! NOK
        context.stop(self)
      }
    }
  }
}

/**
 * This clock listens the bus on a given topic and reacts on the received message.
 * It is responsible to handle a pool of clocks for the monitored frequencies.
 */
class Clock(timeout: Timeout = Timeout(100.milliseconds)) extends Component with ClockChannel {
  import ClockChannel.{ OK, StartClock, StopClock, StopAllClocks }

  override def preStart() = {  
    subscribeOnBus(self)
  }

  def acquire = {
    case msg: StartClock => context.become(running(Map.empty[Long, ActorRef]))
  } 

  /**
   * Running state.
   *
   * @param buffer: Buffer of all ClockChild started, referenced by their frequencies in nanoseconds.
   */
  def running(buffer: Map[Long, ActorRef]): Actor.Receive = {
    case msg: StartClock => start(buffer, msg.frequency, msg.report)
    case msg: StopClock => stop(buffer, msg)
    case StopAllClocks => stopAll(buffer)
  }

  /**
   * Starts a new clock at a given frequency whether is needed.
   * 
   * @param buffer: Buffer which contains all references to the clock children.
   * @param frequency: Clock frequency.
   */
  def start(buffer: Map[Long, ActorRef], frequency: FiniteDuration, report: Report) = {
    val nanoSecs = frequency.toNanos
    val clock = buffer.getOrElse(nanoSecs, None)

    clock match {
      case None => {
        val props = Props(classOf[ClockChild], frequency)
        val child = context.actorOf(props)
        val ack = Await.result(child.?(StartClock(frequency, report))(timeout), timeout.duration)

        if(ack == OK) {
          context.become(running(buffer + (nanoSecs -> child)))
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
        val ack = Await.result(ref.?(msg)(timeout), timeout.duration)

        if(ack == OK) {
          context.become(running(buffer - nanoSecs))
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
        Await.result(ref.?(StopAllClocks)(timeout), timeout.duration)
      }
    })

    context.become(acquire)
  }
}