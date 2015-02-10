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

import java.util.UUID
import scala.concurrent.duration.DurationInt
import akka.actor.ActorRef
import org.powerapi.core.{MessageBus, Channel}

/**
 * PowerChannel channel and messages.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
object PowerChannel extends Channel {
  import org.powerapi.core.Message
  import org.powerapi.core.ClockChannel.ClockTick
  import org.powerapi.core.power._
  import org.powerapi.core.target.{intToProcess, Target}

  type M = PowerReport

  case object PowerReport

  /**
   * Base trait for each power report
   */
  trait PowerReport extends Message {
    def muid: UUID
    def target: Target
    def power: Power
    def device: String
    def tick: ClockTick
    override def toString() = s"timestamp=${tick.timestamp};target=$target;device=$device;value=$power"
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
   * Used to represent an aggregated PowerReport.
   *
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param aggFunction: aggregate power estimation for a specific sample of power reports.
   */
  case class AggregateReport(muid: UUID, aggFunction: Seq[Power] => Power) extends PowerReport {
    private val values: collection.mutable.Buffer[Power] = collection.mutable.Buffer.empty
    private lazy val agg = aggFunction(values.seq)
    private var lastPowerReport: PowerReport = RawPowerReport(aggPowerReportTopic(muid),
                                                              muid,
                                                              -1,
                                                              0.W,
                                                              "none",
                                                              ClockTick("none", 0.milliseconds))
    
    def size: Int = values.size
    def +=(value: PowerReport):AggregateReport = {
      values append value.power
      lastPowerReport = value
      this
    }
    
    val topic: String = aggPowerReportTopic(muid)
    def target: Target = lastPowerReport.target
    def power: Power = agg
    def device: String = lastPowerReport.device
    def tick: ClockTick = lastPowerReport.tick
  }

  /**
   * Publish a power report in the event bus.
   */
  def publishPowerReport(muid: UUID, target: Target, power: Power, device: String, tick: ClockTick): MessageBus => Unit = {
    publish(RawPowerReport(powerReportMuid(muid), muid, target, power, device, tick))
  }
  
  /**
   * Publish an aggregated power report in the event bus.
   */
  def render(aggR: AggregateReport): MessageBus => Unit = {
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
  def subscribePowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    subscribe(powerReportMuid(muid))
  }

  def unsubscribePowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    unsubscribe(powerReportMuid(muid))
  }
  
  /**
   * Use to format a MUID to an associated topic.
   */
  private def powerReportMuid(muid: UUID): String  = {
    s"power:$muid"
  }
  
  private def aggPowerReportTopic(muid: UUID): String = {
    s"reporter:$muid"
  }
}
