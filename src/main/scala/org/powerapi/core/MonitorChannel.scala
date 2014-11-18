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

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorRef

/**
 * Monitor channel and messages.
 */
object MonitorChannel extends Channel {
  
  type M = MonitorMessage

  trait MonitorMessage extends Message

  /**
   * MonitorTarget is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   * @param target: monitor target.
   */
  case class MonitorTarget(topic: String,
                           suid: UUID,
                           target: Target) extends MonitorMessage with Report

  /**
   * MonitorStart is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   * @param frequency: clock frequency.
   * @param targets: monitor targets.
   */
  case class MonitorStart(topic: String,
                          suid: UUID,
                          frequency: FiniteDuration,
                          targets: List[Target]) extends MonitorMessage

  /**
   * MonitorStop is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   */
  case class MonitorStop(topic: String, suid: UUID) extends MonitorMessage

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

  
  def subscribeTarget: MessageBus => ActorRef => Unit = {
    subscribe(topicToPublish) _
  }

  /**
   * Methods used by the API for interacting with the Monitors actor.
   */
  def startMonitor(suid: UUID, frequency: FiniteDuration, targets: List[Target]): MessageBus => Unit = {
    publish(MonitorStart(topic, suid, frequency, targets)) _
  }

  def stopMonitor(suid: UUID): MessageBus => Unit = {
    publish(MonitorStop(topic, suid)) _
  }

  /**
   * Helper for creating the last message for stopping all the Monitor actors.
   */
  lazy val stopAllMonitor = MonitorStopAll(topic)

  /**
   * Internal methods used by the Monitor children for interacting with the bus.
   */
  def subscribeHandlingMonitor: MessageBus => ActorRef => Unit = {
    subscribe(topic) _
  }

  def publishTarget(suid: UUID, target: Target): MessageBus => Unit = {
    publish(MonitorTarget(topicToPublish, suid, target)) _
  }

  /**
   * Use to format the subscription child name.
   */
  def formatMonitorName(suid: UUID) = {
    s"monitor-$suid"
  }
}
