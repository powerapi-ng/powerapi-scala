/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2016 Inria, University of Lille 1.
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
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.core

import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorRef

/**
  * Clock channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object ClockChannel extends Channel {

  type M = ClockMessage
  /**
    * Topic for communicating with the Clock.
    */
  private val topic = "clock:handling"

  /**
    * Used to subscribe/unsubscribe to ClockTick on the right topic.
    */
  def subscribeClockTick(frequency: FiniteDuration): MessageBus => ActorRef => Unit = {
    subscribe(clockTickTopic(frequency))
  }

  def unsubscribeClockTick(frequency: FiniteDuration): MessageBus => ActorRef => Unit = {
    unsubscribe(clockTickTopic(frequency))
  }

  /**
    * Used to interact with the Supervisor.
    */
  def startClock(frequency: FiniteDuration): MessageBus => Unit = {
    publish(ClockStart(topic, frequency))
  }

  def stopClock(frequency: FiniteDuration): MessageBus => Unit = {
    publish(ClockStop(topic, frequency))
  }

  def stopAllClock: MessageBus => Unit = {
    publish(ClockStopAll(topic))
  }

  /**
    * Used to subscribe to ClockMessage on the right topic.
    */
  def subscribeClockChannel: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }

  /**
    * Used to publish ClockTick on the right topic.
    */
  def publishClockTick(frequency: FiniteDuration): MessageBus => Unit = {
    publish(ClockTick(clockTickTopic(frequency)))
  }

  /**
    * Use to format a frequency to an associated topic.
    */
  private def clockTickTopic(frequency: FiniteDuration): String = {
    s"tick:${frequency.toNanos}"
  }

  /**
    * Use to format the ClockChild name.
    */
  def formatClockChildName(frequency: FiniteDuration): String = {
    s"clock-${frequency.toNanos}"
  }

  trait ClockMessage extends Message

  /**
    * ClockTick is represented as a dedicated type of message.
    *
    * @param topic subject used for routing the message.
    */
  case class ClockTick(topic: String, timestamp: Long = System.currentTimeMillis) extends ClockMessage with Tick

  /**
    * ClockStart is represented as a dedicated type of message.
    *
    * @param topic     subject used for routing the message.
    * @param frequency clock frequency.
    */
  case class ClockStart(topic: String, frequency: FiniteDuration) extends ClockMessage

  /**
    * ClockStop is represented as a dedicated type of message.
    *
    * @param topic     subject used for routing the message.
    * @param frequency clock frequency.
    */
  case class ClockStop(topic: String, frequency: FiniteDuration) extends ClockMessage

  /**
    * ClockStopAll is represented as a dedicated type of message.
    *
    * @param topic subject used for routing the message.
    */
  case class ClockStopAll(topic: String) extends ClockMessage

}
