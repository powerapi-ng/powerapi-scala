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
package org.powerapi.module

import akka.actor.ActorRef
import java.util.UUID
import org.powerapi.core.{Message, MessageBus, Channel}
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import scala.concurrent.duration.DurationInt

/**
 * PowerChannel channel and messages.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
object PowerChannel extends Channel {

  type M = PowerReport

  /**
   * Base trait for each power report
   */
  trait PowerReport extends Message {
    def muid: UUID
    def tick: ClockTick
  }

  /**
   * RawPowerReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param power: target's power consumption.
   * @param device: device targeted.
   * @param tick: tick origin.
   */
  case class RawPowerReport(topic: String,
                            muid: UUID,
                            target: Target,
                            power: Power,
                            device: String,
                            tick: ClockTick) extends PowerReport
                         
  /**
   * AggregatePowerReport is represented as a dedicated type of message.
   */
  case class AggregatePowerReport(muid: UUID, aggFunction: Seq[Power] => Power) extends PowerReport {
    private val reports = collection.mutable.Buffer[RawPowerReport]()
    private lazy val agg = aggFunction(for(report <- reports) yield report.power)
    
    def size: Int = reports.size

    def +=(report: RawPowerReport): AggregatePowerReport = {
      reports += report
      this
    }
    
    val topic: String = aggPowerReportTopic(muid)
    def targets: Set[Target] = (for(report <- reports) yield report.target).toSet
    def power: Power = agg
    def devices: Set[String] = (for(report <- reports) yield report.device).toSet
    def tick: ClockTick = if(reports.nonEmpty) reports.last.tick else ClockTick("", 0.seconds)
  }

  /**
   * Publish a raw power report in the event bus.
   */
  def publishRawPowerReport(muid: UUID, target: Target, power: Power, device: String, tick: ClockTick): MessageBus => Unit = {
    publish(RawPowerReport(rawPowerReportMuid(muid), muid, target, power, device, tick))
  }
  
  /**
   * Publish an aggregated power report in the event bus.
   */
  def render(aggR: AggregatePowerReport): MessageBus => Unit = {
    publish(aggR)
  }
  
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
  def subscribeRawPowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    subscribe(rawPowerReportMuid(muid))
  }

  def unsubscribeRawPowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    unsubscribe(rawPowerReportMuid(muid))
  }
  
  /**
   * Use to format a MUID to an associated topic.
   */
  private def rawPowerReportMuid(muid: UUID): String  = {
    s"power:$muid"
  }
  
  private def aggPowerReportTopic(muid: UUID): String = {
    s"reporter:$muid"
  }
}
