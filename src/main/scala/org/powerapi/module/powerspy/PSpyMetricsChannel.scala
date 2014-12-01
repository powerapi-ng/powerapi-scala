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
package org.powerapi.module.powerspy

import java.util.UUID

import akka.actor.ActorRef
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.{TargetUsageRatio, MessageBus, Target}
import org.powerapi.module.{SensorReport, SensorChannel}

/**
 * PSpyMetrics channel and messages.
 *
 * @author Aurélien Bourdon <aurelien.bourdon@gmail.com>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object PSpyMetricsChannel extends SensorChannel {
  /**
   * Internal messages used between the Sensor and the Listener.
   */
  object PSpyStart
  case class PSpyChildMessage(rms: Double, uScale: Float, iScale: Float) {
    def +(that: PSpyChildMessage): PSpyChildMessage = PSpyChildMessage(rms + that.rms, uScale + that.uScale, iScale + that.iScale)
    def /(that: Int): Option[PSpyChildMessage] = if(that != 0) Some(PSpyChildMessage(rms / that, uScale / that, iScale / that)) else None
  }
  object PSpyChildMessage {
    def avg(messages: List[PSpyChildMessage]): Option[PSpyChildMessage] = {
      messages.foldLeft(PSpyChildMessage(0.0, 0f, 0f))((acc, msg) => acc + msg) / messages.size
    }
  }

  trait PSpyDataReport extends SensorReport

  /**
   * PSpyAllDataReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param rms: current root mean square.
   * @param uScale: tension, voltage.
   * @param iScale: intensity.
   * @param tick: tick origin.
   */
  case class PSpyAllDataReport(topic: String,
                               muid: UUID,
                               target: Target,
                               rms: Double,
                               uScale: Float,
                               iScale: Float,
                               tick: ClockTick) extends PSpyDataReport

  /**
   * PSpyRatioDataReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param targetRatio: target cpu percent usage.
   * @param rms: current root mean square.
   * @param uScale: tension, voltage.
   * @param iScale: intensity.
   * @param tick: tick origin.
   */
  case class PSpyRatioDataReport(topic: String,
                                 muid: UUID,
                                 target: Target,
                                 targetRatio: TargetUsageRatio,
                                 rms: Double,
                                 uScale: Float,
                                 iScale: Float,
                                 tick: ClockTick) extends PSpyDataReport

  /**
   * Topic for communicating with the Formula actors.
   */
  private val topicAll = "sensor:powerspy-all"
  private val topicRatio = "sensor:powerspy-ratio"

  /**
   * Publish a PSpyDataReport in the event bus.
   */
  def publishPSpyDataReport(muid: UUID, target: Target, rms: Double, uScale: Float, iScale: Float, tick: ClockTick): MessageBus => Unit = {
    publish(PSpyAllDataReport(topicAll, muid, target, rms, uScale, iScale, tick))
  }

  def publishPSpyDataReport(muid: UUID, target: Target, targetRatio: TargetUsageRatio, rms: Double, uScale: Float, iScale: Float, tick: ClockTick): MessageBus => Unit = {
    publish(PSpyRatioDataReport(topicRatio, muid, target, targetRatio, rms, uScale, iScale, tick))
  }

  /**
   * External method used by the Formula for interacting with the bus.
   */
  def subscribePSpyAllDataReport: MessageBus => ActorRef => Unit = {
    subscribe(topicAll)
  }

  def subscribePSpyRatioDataReport: MessageBus => ActorRef => Unit = {
    subscribe(topicRatio)
  }
}
