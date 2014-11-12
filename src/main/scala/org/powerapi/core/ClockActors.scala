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
import akka.actor.SupervisorStrategy.{ Directive, Resume }
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout

/**
 * One child clock is created per frequency.
 * Allows to publish a message in the right topics for a given frequency.
 * A clock child actor is called by its frequency in nanoseconds for lookups.
 */
class ClockChild(frequency: FiniteDuration) extends Component {
  import ClockChannel.{ ClockAlreadyStarted, ClockStarted, ClockStillRunning, ClockStopped }
  import ClockChannel.{ publishTick, ClockStart, ClockStopAll, ClockStop }

  def receive = LoggingReceive {
    case ClockStart(_, freq) if frequency == freq => start()
  } orElse default

  /**
   * Running state, only one timer per ClockChild.
   * An accumulator is used to know how many subscriptions are using this frequency.
   *
   * @param acc: Accumulator used to know the number of subscriptions which run at this frequency.
   * @param timer: Timer created for producing ticks.
   */
  def running(acc: Int, timer: Cancellable): Actor.Receive = LoggingReceive {
    case ClockStart(_, freq) if frequency == freq => {
      log.debug("clock is already started, reference: {}", frequency.toNanos)
      sender ! ClockAlreadyStarted(frequency)
      context.become(running(acc + 1, timer))
    }
    case ClockStop(_, freq) if frequency == freq => stop(acc, timer)
    case _: ClockStopAll => stop(1, timer, false)
  } orElse default

  /**
   * Start the clock and the associated scheduler for publishing a Tick on the required topic at a given frequency.
   */
  def start() = {
    val timer = context.system.scheduler.schedule(Duration.Zero, frequency) {
      publishTick(frequency)
    } (context.system.dispatcher)

    log.debug("clock started, reference: {}", frequency.toNanos)
    sender ! ClockStarted(frequency)
    context.become(running(1, timer))
  }

  /**
   * Stop the clock and the scheduler.
   * Send an ack to the sender if needed.
   *
   * @param acc: Accumulator used to know the number of subscriptions which run at this frequency.
   * @param timer: Timer created for producing ticks.
   */
  def stop(acc: Int, timer: Cancellable, ack: Boolean = true) = {
    if(acc > 1) {
      log.debug("this frequency is still used, clock is still running, reference: {}", frequency.toNanos)
      sender ! ClockStillRunning(frequency)
      context.become(running(acc - 1, timer))
    }
    else {
      timer.cancel
      log.debug("clock stopped, reference: {}", frequency.toNanos)
      if(ack) sender ! ClockStopped(frequency)
    }
  }
}

/**
 * This clock listens the bus on a given topic and reacts on the received message.
 * It is responsible to handle a pool of clocks for the monitored frequencies.
 */
class Clock(timeout: Timeout = Timeout(100.milliseconds)) extends Component with Supervisor {
  import ClockChannel.{ ClockAlreadyStarted, ClockStarted, ClockStopped, ClockStart }
  import ClockChannel.{ ClockStopAll, ClockStop, subscribeTickSubscription }

  override def preStart() = {  
    subscribeTickSubscription(self)
  }

  /**
   * ClockChild actors can only launch exception if the message received is not handled.
   */
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume 
  }

  def receive = LoggingReceive {
    case msg: ClockStart => start(msg)
  } orElse default

  /**
   * Running state.
   */
  def running: Actor.Receive = LoggingReceive {
    case msg: ClockStart => start(msg)
    case msg: ClockStop => stop(msg)
    case msg: ClockStopAll => stopAll(msg)
  } orElse default

  /**
   * Start a new clock at a given frequency whether is needed.
   * 
   * @param msg: Message received for starting a clock at a given frequency.
   */
  def start(msg: ClockStart) = {
    val nanoSecs = msg.frequency.toNanos

    val child = context.child(s"$nanoSecs") match {
      case Some(actorRef) => actorRef 
      case None => context.actorOf(Props(classOf[ClockChild], msg.frequency), s"$nanoSecs")
    }

    Await.result(child.?(msg)(timeout), timeout.duration)
    context.become(running)
  }

  /**
   * Stop a clock for a given frequency if it exists.
   * 
   * @param msg: Message received for stopping a clock at a given frequency.
   */
  def stop(msg: ClockStop) = {
    val nanoSecs = msg.frequency.toNanos

    context.child(s"$nanoSecs") match {
      case Some(child) => {
        val ack = Await.result(child.?(msg)(timeout), timeout.duration)

        if(ack == ClockStopped(msg.frequency)) {
          context.stop(child)
        }
      } 
      case None => throw new UnsupportedOperationException(s"clock does not exist, reference: $nanoSecs")
    }
  }

  /**
   * Stop all clocks for all frequencies.
   *
   * @param msg: Message received for stopping all clocks.
   */
  def stopAll(msg: ClockStopAll) = {
    context.actorSelection("*") ! msg
    context.become(receive)
  }
}
