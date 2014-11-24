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
package org.powerapi.sensors.procfs.cpu

import java.util.UUID
import akka.actor.ActorRef
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.{Channel, MessageBus, Target}

/**
 * CpuProcfsSensorChannel channel and messages.
 *
 * @author Aurélien Bourdon <aurelien@bourdon@gmail.com>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object CpuProcfsSensorChannel extends Channel {

  type M = SensorReport

  /**
   * Wrapper classes.
   */
  case class TimeInStates(times: Map[Int, Long]) {
    def -(that: TimeInStates) =
      TimeInStates((for ((frequency, time) <- times) yield (frequency, time - that.times.getOrElse(frequency, 0: Long))).toMap)
  }
  case class TargetRatio(percent: Double = 0)
  case class CacheKey(muid: UUID, target: Target)

  /**
   * CpuProcfsSensorReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param targetRatio: target cpu percent usage.
   * @param timeInStates: time spent by the CPU in its frequencies.
   * @param tick: tick origin.
   */
  case class CpuProcfsSensorReport(topic: String,
                                   muid: UUID,
                                   target: Target,
                                   targetRatio: TargetRatio,
                                   timeInStates: TimeInStates = TimeInStates(Map()),
                                   tick: ClockTick) extends SensorReport

  /**
   * Topic for communicating with the Formula actors.
   */
  private val topicProcfsSimple = "sensor:cpu-procfs-simple"
  private val topicProcfsDvfs = "sensor:cpu-procfs-dvfs"

  /**
   * Publish a CpuProcfsSensorReport in the event bus.
   */
  def publishCpuProcfsReport(muid: UUID, target: Target, targetRatio: TargetRatio, tick: ClockTick): MessageBus => Unit = {
    publish(CpuProcfsSensorReport(topic = topicProcfsSimple,
                                  muid = muid,
                                  target = target,
                                  targetRatio = targetRatio,
                                  tick = tick))
  }

  def publishCpuProcfsReport(muid: UUID, target: Target, targetRatio: TargetRatio, timeInStates: TimeInStates, tick: ClockTick): MessageBus => Unit = {
    publish(CpuProcfsSensorReport(topic = topicProcfsDvfs,
                                  muid = muid,
                                  target = target,
                                  targetRatio = targetRatio,
                                  timeInStates = timeInStates,
                                  tick = tick))
  }

  /**
   * External method used by the Formula for interacting with the bus.
   */
  def subscribeCpuProcfsSensor: MessageBus => ActorRef => Unit = {
    subscribe(topicProcfsSimple)
  }

  def subscribeCpuProcfsDvfsSensor: MessageBus => ActorRef => Unit = {
    subscribe(topicProcfsDvfs)
  }
}
