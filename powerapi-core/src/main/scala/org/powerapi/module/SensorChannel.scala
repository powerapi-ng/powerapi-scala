/*
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
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.module

import org.powerapi.core.Channel

/**
 * Base channel for the Sensor components.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object SensorChannel extends Channel {
  import akka.actor.ActorRef
  import java.util.UUID
  import org.powerapi.core.ClockChannel.ClockTick
  import org.powerapi.core.{Message, MessageBus}
  import org.powerapi.core.target.Target

  type M = SensorMessage

  /**
   * Main sensor messages.
   *
   * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
   */
  trait SensorMessage extends Message

  trait SensorReport extends SensorMessage {
    def topic: String
    def muid: UUID
    def target: Target
    def tick: ClockTick
  }

  case class MonitorStop(topic: String, muid: UUID) extends SensorMessage
  case class MonitorStopAll(topic: String) extends SensorMessage

  private val topic = "sensor:handling"

  /**
   * Internal method used by the Sensors for reacting when a Monitor is stopped.
   */
  def subscribeSensorsChannel: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }

  /**
   * External methods used by the Monitors when a Monitor is stopped.
   * Act like callbacks.
   */
  def monitorStopped(muid: UUID): MessageBus => Unit = {
    publish(MonitorStop(topic, muid))
  }

  def monitorAllStopped(): MessageBus => Unit = {
    publish(MonitorStopAll(topic))
  }
}
