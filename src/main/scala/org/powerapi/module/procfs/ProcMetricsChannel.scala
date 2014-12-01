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
package org.powerapi.module.procfs

import java.util.UUID

import akka.actor.ActorRef
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.{MessageBus, Target, TimeInStates}
import org.powerapi.module.{SensorReport, SensorChannel}

/**
 * ProcMetricsChannel channel and messages.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object ProcMetricsChannel extends SensorChannel {

  /**
   * Wrapper classes.
   */
  case class TargetUsageRatio(ratio: Double = 0)
  case class CacheKey(muid: UUID, target: Target)

  /**
   * UsageReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param targetRatio: target cpu ratio usage.
   * @param timeInStates: time spent by the CPU in its frequencies.
   * @param tick: tick origin.
   */
  case class UsageReport(topic: String,
                         muid: UUID,
                         target: Target,
                         targetRatio: TargetUsageRatio,
                         timeInStates: TimeInStates = TimeInStates(Map()),
                         tick: ClockTick) extends SensorReport

  /**
   * Topic for communicating with the Formula actors.
   */
  private val topicSimpleUsageReport = "sensor:cpu-procfs-simple"
  private val topicDvfsUsageReport = "sensor:cpu-procfs-dvfs"

  /**
   * Publish a UsageReport in the event bus.
   */
  def publishUsageReport(muid: UUID, target: Target, targetRatio: TargetUsageRatio, tick: ClockTick): MessageBus => Unit = {
    publish(UsageReport(topic = topicSimpleUsageReport,
                        muid = muid,
                        target = target,
                        targetRatio = targetRatio,
                        tick = tick))
  }

  def publishUsageReport(muid: UUID, target: Target, targetRatio: TargetUsageRatio, timeInStates: TimeInStates, tick: ClockTick): MessageBus => Unit = {
    publish(UsageReport(topic = topicDvfsUsageReport,
                        muid = muid,
                        target = target,
                        targetRatio = targetRatio,
                        timeInStates = timeInStates,
                        tick = tick))
  }

  /**
   * External method used by the Formula for interacting with the bus.
   */
  def subscribeSimpleUsageReport: MessageBus => ActorRef => Unit = {
    subscribe(topicSimpleUsageReport)
  }

  def subscribeDvfsUsageReport: MessageBus => ActorRef => Unit = {
    subscribe(topicDvfsUsageReport)
  }
}
