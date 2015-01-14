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

import org.powerapi.core.{ Channel, MessageBus }

/**
 * Power units.
 *
 * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
object PowerUnit extends Enumeration {

  case class PowerUnit(name: String, description: String) extends Val

  val W = PowerUnit("W", "Watts")
  val kW = PowerUnit("kW", "KiloWatts")
}

/**
 * PowerChannel channel and messages.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object PowerChannel extends Channel {
  import org.powerapi.core.{ Message, Process, Target }
  import org.powerapi.core.ClockChannel.ClockTick
  import org.powerapi.module.PowerUnit.{ PowerUnit, W }

  type M = PowerReport

  /**
   * FormulaReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param power: target's power consumption.
   * @param unit: power unit.
   * @param device: device targeted.
   * @param tick: tick origin.
   */
  case class PowerReport(topic: String,
                         muid: UUID,
                         target: Target,
                         power: Double,
                         unit: PowerUnit,
                         device: String,
                         tick: ClockTick) extends Message
                         
  /**
   * Used to represent an aggregated PowerReport.
   */
  trait AggregateReport[T] {
    private val values: collection.mutable.Set[T] = collection.mutable.Set.empty
    def aggFunct(l: List[T]): Option[T]
    
    def +=(value: T) { values add value }
    def getValues: collection.mutable.Set[T] = values.clone
    def size: Int = values.size
    
    lazy val agg = aggFunct(values.toList)
  }
  
  /**
   * Initialize an empty aggregated power report which contains power reports
   * from a same MUID and which computes aggregated power based on these power reports.
   *
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param aggFunction: aggregate power reports of a same monitor.
   */
  def aggregatePowerReports(muid: UUID, aggFunction: List[PowerReport] => Option[PowerReport]): AggregateReport[PowerReport] = {
    new PowerReport(aggPowerReportTopic(muid), muid, Process(-1), 0.0, W, "none", ClockTick("none", 0.milliseconds))
      with AggregateReport[PowerReport] {
        override def aggFunct(l: List[PowerReport]): Option[PowerReport] = aggFunction(l)
      }
  }

  /**
   * Publish a PowerReport in the event bus.
   */
  def publishPowerReport(muid: UUID, target: Target, power: Double, unit: PowerUnit, device: String, tick: ClockTick): MessageBus => Unit = {
    publish(PowerReport(powerReportMuid(muid), muid, target, power, unit, device, tick))
  }
  
  /**
   * Publish an aggregated PowerReport in the event bus.
   */
  def render(aggR: AggregateReport[PowerReport]): MessageBus => Unit = {
    aggR.agg match {
      case Some(aggPowerReport) => publish(
        new PowerReport(aggPowerReportTopic(aggPowerReport.muid),
                        aggPowerReport.muid,
                        aggPowerReport.target,
                        aggPowerReport.power,
                        aggPowerReport.unit,
                        aggPowerReport.device,
                        aggPowerReport.tick)
          with AggregateReport[PowerReport] {
            override def aggFunct(l: List[PowerReport]): Option[PowerReport] = aggR.aggFunct(l)
            aggR.getValues.foreach(this += _)
          }
        )
      case None => publish(PowerReport(aggPowerReportTopic(UUID.fromString("0")),
                                       UUID.fromString("0"),
                                       Process(-1),
                                       0.0,
                                       W,
                                       "none",
                                       ClockTick("none", 0.milliseconds)))
    }
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
