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
      if(log.isDebugEnabled) log.debug(new StringContext("clock is already started, reference: ", "").s(frequency.toNanos))
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
      case None => {
        if(log.isErrorEnabled) log.error(new StringContext("the timer for the clock referenced ", "", " was not started.").s(frequency.toNanos))
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
  import ClockChannel.{ OK, StartClock, StopAllClocks, StopClock }

  override def preStart() = {  
    subscribeOnBus(self)
  }

  def acquire = {
    case msg: StartClock => start(Map.empty[Long, ActorRef], msg.frequency, msg.report)
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
    val child = buffer.getOrElse(nanoSecs, {
      context.actorOf(Props(classOf[ClockChild], frequency))
    })
    
    val ack = Await.result(child.?(StartClock(frequency, report))(timeout), timeout.duration)

    if(ack == OK) {
      if(buffer.contains(nanoSecs)) {
        context.become(running(buffer))
      }
      else context.become(running(buffer + (nanoSecs -> child)))
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
