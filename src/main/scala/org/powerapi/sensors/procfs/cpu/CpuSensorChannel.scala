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
import org.powerapi.core.{Channel, Message, MessageBus, Target}


/**
 * Monitor channel and messages.
 *
 * @author abourdon
 * @author mcolmant
 */
object CpuSensorChannel extends Channel {

  type M = CpuSensorReport

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
   * CpuSensorReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param targetRatio: target cpu percent usage.
   * @param timeInStates: time spent by the CPU in its frequencies.
   * @param timestamp: Origin time for the ClockTick message.
   */
  case class CpuSensorReport(topic: String,
                             muid: UUID,
                             target: Target,
                             targetRatio: TargetRatio,
                             timeInStates: TimeInStates = TimeInStates(Map()),
                             timestamp: Long) extends Message

  /**
   * Topic for communicating with the Formula actors.
   */
  private val topicProc = "sensor:proc"
  private val topicProcDvfs = "sensor:proc-dvfs"

  /**
   * Publish a CpuSensorReport in the event bus.
   */
  def publishCpuReport(muid: UUID, target: Target, targetRatio: TargetRatio, timestamp: Long): MessageBus => Unit = {
    publish(CpuSensorReport(topic = topicProc,
                            muid = muid,
                            target = target,
                            targetRatio = targetRatio,
                            timestamp = timestamp))
  }

  def publishCpuReport(muid: UUID, target: Target, targetRatio: TargetRatio, timeInStates: TimeInStates, timestamp: Long): MessageBus => Unit = {
    publish(CpuSensorReport(topic = topicProc,
                            muid = muid,
                            target = target,
                            targetRatio = targetRatio,
                            timeInStates = timeInStates,
                            timestamp = timestamp))
  }

  /**
   * External method use by the Formula for interacting with the bus.
   */
  def subscribeCpuProcSensor: MessageBus => ActorRef => Unit = {
    subscribe(topicProc)
  }

  def subscribeCpuProcDvfsSensor: MessageBus => ActorRef => Unit = {
    subscribe(topicProcDvfs)
  }
}
