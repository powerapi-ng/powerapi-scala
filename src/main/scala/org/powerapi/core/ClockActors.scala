/**
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 * 
 * Copyright (C) 2011-2014 Inria, University of Lille 1.
 * 
 * PowerAPI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * PowerAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with PowerAPI.

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.core

import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
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
  import ClockChannel.{ ClockAlreadyStarted, ClockStarted, ClockStillRunning, ClockStopped, ClockTick }
  import ClockChannel.{ clockTickTopic, StartClock, StopAllClocks, StopClock, topic }

  def acquire = {
    case StartClock(_, report) => start(report)(clockTickTopic(frequency))
  }

  /**
   * Running state, only one timer per ClockChild.
   * An accumulator is used to know how many subscriptions are using this frequency.
   *
   * @param acc: Accumulator used to know the number of subscriptions which run at this frequency.
   * @param topic: Topic where messages will be published at each tick.
   * @param timer: Timer created for producing ticks.
   */
  def running(acc: Int)(topic: String)(timer: Cancellable): Actor.Receive = {
    case StartClock(_, _) => {
      log.debug("clock is already started, reference: {}", frequency.toNanos)
      sender ! ClockAlreadyStarted(frequency)
      context.become(running(acc + 1)(topic)(timer))
    }
    case StopClock(_) => stop(acc)(topic)(timer)
    case StopAllClocks => stop(1)(topic)(timer)
  }

  /**
   * Start the clock and the associated scheduler for publishing a Tick on the required topics at a given frequency.
   * 
   * @param report: Base message sent on the bus, received by the parent.
   * @param topic: Topic where messages will be published at each tick.
   */
  def start(report: Report)(topic: String) = {
    val timer = context.system.scheduler.schedule(Duration.Zero, frequency) {
      sendTick(ClockTick(report.suid, topic, frequency))
    } (context.system.dispatcher)

    log.debug("clock started, reference: {}", frequency.toNanos)
    sender ! ClockStarted(frequency)
    context.become(running(1)(topic)(timer))
  }

  /**
   * Stop the clock and the scheduler.
   *
   * @param acc: Accumulator used to know the number of subscriptions which run at this frequency.
   * @param topic: Topic where messages will be published at each tick.
   * @param timer: Timer created for producing ticks.
   */
  def stop(acc: Int)(topic: String)(timer: Cancellable) = {
    if(acc > 1) {
      log.debug("this frequency is still used, clock is still running, reference: {}", frequency.toNanos)
      sender ! ClockStillRunning(frequency)
      context.become(running(acc - 1)(topic)(timer))
    }
    else {
      timer.cancel
      log.debug("clock stopped, reference: {}", frequency.toNanos)
      sender ! ClockStopped(frequency)
      context.stop(self)
    }
  }
}

/**
 * This clock listens the bus on a given topic and reacts on the received message.
 * It is responsible to handle a pool of clocks for the monitored frequencies.
 */
class Clock(timeout: Timeout = Timeout(100.milliseconds)) extends Component with ClockChannel {
  import ClockChannel.{ ClockAlreadyStarted, ClockStarted, ClockStopped, StartClock, StopAllClocks, StopClock }

  override def preStart() = {  
    receiveTickSubscription(self)
  }

  def acquire = {
    case StartClock(frequency, report) => start(Map.empty[Long, ActorRef], frequency, report)
  } 

  /**
   * Running state.
   *
   * @param buffer: Buffer of all ClockChild started, referenced by their frequencies in nanoseconds.
   */
  def running(buffer: Map[Long, ActorRef]): Actor.Receive = {
    case StartClock(frequency, report) => start(buffer, frequency, report)
    case msg: StopClock => stop(buffer, msg)
    case StopAllClocks => stopAll(buffer)
  }

  /**
   * Start a new clock at a given frequency whether is needed.
   * 
   * @param buffer: Buffer which contains all references to the clock children.
   * @param frequency: Clock frequency.
   */
  def start(buffer: Map[Long, ActorRef], frequency: FiniteDuration, report: Report) = {
    val nanoSecs = frequency.toNanos
    val child = buffer.getOrElse(nanoSecs, {
      context.actorOf(Props(classOf[ClockChild], frequency))
    })
    
    val ack = Await.result(child.?(StartClock(frequency, report))(timeout), timeout.duration)

    if(ack == ClockStarted(frequency)) {
      context.become(running(buffer + (nanoSecs -> child)))
    }
  }

  /**
   * Stop a clock for a given frequency when is needed.
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

        if(ack == ClockStopped(msg.frequency)) {
          context.become(running(buffer - nanoSecs))
        }
      }
      case _ => if(log.isDebugEnabled) log.debug(s"clock does not exist, reference: $nanoSecs")
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
