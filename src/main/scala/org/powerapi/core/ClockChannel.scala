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
  type R = ClockTick

  /**
   * ClockTick is represented as a dedicated type of report.
   * 
   * @param suid: subscription UID of the report.
   * @param topic: subject used for routing the message.
   * @param frequency: clock frequency.
   */
  case class ClockTick(suid: Long,
                       topic: String,
                       frequency: FiniteDuration,
                       timestamp: Long = System.currentTimeMillis) extends Report

  /**
   * Messages.
   */
  case class StartClock(frequency: FiniteDuration, report: Report)
  case class StopClock(frequency: FiniteDuration)
  
  object StopAllClocks

  object OK
  object NOK

  private val topic = "tick:subscription"

  def subscribe: EventBus => ActorRef => Unit = subscribe(topic)

  def formatTopicFromFrequency(frequency: FiniteDuration) = {
    new StringContext("tick:", "").s(frequency.toNanos)
  }
}

/**
 * Used to inject the bus for different actors.
 */
trait ClockChannel {
  import ClockChannel.{ ClockTick, publish, subscribe }

  def subscribeOnBus: ActorRef => Unit = subscribe(ReportBus.eventBus)
  def publishOnBus: ClockTick => Unit = publish(ReportBus.eventBus)
}
