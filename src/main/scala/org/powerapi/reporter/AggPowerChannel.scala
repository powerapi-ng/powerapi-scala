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
package org.powerapi.reporter

import java.util.UUID

import akka.actor.ActorRef

import org.powerapi.core.{ Channel, MessageBus }

/**
 * Reporter channel and messages.
 *
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
object AggPowerChannel extends Channel {
  import org.powerapi.core.{ Message, Target }
  import org.powerapi.core.ClockChannel.ClockTick
  import org.powerapi.module.PowerChannel.PowerReport
  import org.powerapi.module.PowerUnit.PowerUnit
  
  type M = AggPowerReport

  /**
   * AggPowerReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param power: target's aggregated power consumption.
   * @param unit: power unit.
   * @param device: device targeted.
   * @param tick: tick origin.
   */
  case class AggPowerReport(topic: String,
                            muid: UUID,
                            target: Target,
                            power: Double,
                            unit: PowerUnit,
                            device: String,
                            tick: ClockTick) extends Message

  /**
   * External methods used by the Reporter components for interacting with the bus.
   */
  def subscribeAggPowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    subscribe(aggPowerReportTopic(muid))
  }
  
  def unsubscribeAggPowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    unsubscribe(aggPowerReportTopic(muid))
  }
  
  /**
   * External method used by the MonitorChild actors for interacting with the bus.
   */
  def render(powerReport: PowerReport): MessageBus => Unit = {
    publish(AggPowerReport(aggPowerReportTopic(powerReport.muid),
                           powerReport.muid,
                           powerReport.target,
                           powerReport.power,
                           powerReport.unit,
                           powerReport.device,
                           powerReport.tick))
  }
  
  /**
   * Use to format a MUID to an associated topic.
   */
  private def aggPowerReportTopic(muid: UUID): String = {
    s"reporter:$muid"
  }
}

