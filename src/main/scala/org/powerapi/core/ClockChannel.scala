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

import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorRef

/**
 * Clock channel and messages.
 */
object ClockChannel extends Channel {
  import MessageBus.eventBus

  type M = ClockMessage

  trait ClockMessage extends Message

  /**
   * ClockTick is represented as a dedicated type of message.
   * 
   * @param topic: subject used for routing the message.
   * @param frequency: clock frequency.
   */
  case class ClockTick(topic: String,
                       frequency: FiniteDuration,
                       timestamp: Long = System.currentTimeMillis) extends ClockMessage

  /**
   * ClockTickSubscription is represented as a dedicated type of message.
   * 
   * @param topic: subject used for routing the message.
   * @param frequency: clock frequency.
   */
  case class ClockTickSubscription(topic: String,
                       frequency: FiniteDuration) extends ClockMessage

  /**
   * ClockStart is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param frequency: clock frequency.
   */
  case class ClockStart(topic: String, frequency: FiniteDuration) extends ClockMessage

  /**
   * ClockStop is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param frequency: clock frequency.
   */
  case class ClockStop(topic: String, frequency: FiniteDuration) extends ClockMessage

  /**
   * ClockStopAll is represented as a dedicated type of message.
   */
  case class ClockStopAll(topic: String) extends ClockMessage

  /**
   * Ack messages.
   */
  case class ClockStarted(frequency: FiniteDuration)
  case class ClockAlreadyStarted(frequency: FiniteDuration)
  case class ClockStillRunning(frequency: FiniteDuration)
  case class ClockStopped(frequency: FiniteDuration)

  private val topic = "tick:subscription"

  /**
   * Methods used by the subscription actors to interact with the clock actors by
   * using the bus.
   */
  def subscribeClock(frequency: FiniteDuration): ActorRef => Unit = {
    subscribe(eventBus, clockTickTopic(frequency)) _
  }

  def unsubscribeClock(frequency: FiniteDuration): ActorRef => Unit = {
    unsubscribe(eventBus, clockTickTopic(frequency)) _
  }

  def startClock(frequency: FiniteDuration) {
    publish(eventBus, ClockStart(topic, frequency))
  }

  def stopClock(frequency: FiniteDuration) {
    publish(eventBus, ClockStop(topic, frequency))
  }

  def stopAllClock() = {
    publish(eventBus, ClockStopAll(topic))
  }

  /**
   * Methods used by the clock actors to interact with the event bus.
   */
  def subscribeTickSubscription: ActorRef => Unit = {
    subscribe(eventBus, topic)
  }

  def publishTick(frequency: FiniteDuration) = {
    publish(eventBus, ClockTick(clockTickTopic(frequency), frequency))
  }

  private def clockTickTopic(frequency: FiniteDuration) = {
    s"tick:${frequency.toNanos}"
  }
}
