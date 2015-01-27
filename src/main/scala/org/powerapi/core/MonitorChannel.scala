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
package org.powerapi.core

import java.util.UUID
import akka.actor.ActorRef
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.target.Target
import scala.concurrent.duration.FiniteDuration

/**
 * Monitor channel and messages.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object MonitorChannel extends Channel {
  
  type M = MonitorMessage

  trait MonitorMessage extends Message

  /**
   * MonitorTick is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param tick: tick origin.
   */
  case class MonitorTick(topic: String,
                         muid: UUID,
                         target: Target,
                         tick: ClockTick) extends MonitorMessage

  /**
   * MonitorStart is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param frequency: clock frequency.
   * @param targets: monitor targets.
   */
  case class MonitorStart(topic: String,
                          muid: UUID,
                          frequency: FiniteDuration,
                          targets: List[Target]) extends MonitorMessage

  /**
   * MonitorStop is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   */
  case class MonitorStop(topic: String, muid: UUID) extends MonitorMessage

  /**
   * MonitorStopAll is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   */
  case class MonitorStopAll(topic: String) extends MonitorMessage

  /**
   * Topic for communicating with the Monitors actor.
   */
  private val topic = "monitor:handling"

  /**
   * Topic for communicating with the Sensor actors.
   */
  private val topicToPublish = "monitor:target"

  /**
   * External methods used by the Sensor actors for interacting with the bus.
   */
  def subscribeMonitorTick: MessageBus => ActorRef => Unit = {
    subscribe(topicToPublish)
  }

  /**
   * External Methods used by the API (or a Monitor object) for interacting with the bus.
   */
  def startMonitor(muid: UUID, frequency: FiniteDuration, targets: List[Target]): MessageBus => Unit = {
    publish(MonitorStart(topic, muid, frequency, targets))
  }

  def stopMonitor(muid: UUID): MessageBus => Unit = {
    publish(MonitorStop(topic, muid))
  }

  def stopAllMonitor: MessageBus => Unit = {
    publish(MonitorStopAll(topic))
  }

  /**
   * Internal methods used by the Monitors actor for interacting with the bus.
   */
  def subscribeMonitorsChannel: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }

  /**
   * Internal methods used by the MonitorChild actors for interacting with the bus.
   */
  def publishMonitorTick(muid: UUID, target: Target, tick: ClockTick): MessageBus => Unit = {
    publish(MonitorTick(topicToPublish, muid, target, tick))
  }

  /**
   * Use to format the MonitorChild name.
   */
  def formatMonitorChildName(muid: UUID): String = {
    s"monitor-$muid"
  }
}
