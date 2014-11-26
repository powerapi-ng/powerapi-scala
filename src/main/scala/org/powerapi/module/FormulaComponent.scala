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
package org.powerapi.module

import java.util.UUID
import akka.actor.ActorRef
import akka.event.LoggingReceive
import org.powerapi.core.{APIComponent, Channel, MessageBus}

/**
 * Base trait for each PowerAPI formula.
 * Each of them should react to a SensorReport, compute the power and then publish a PowerReport.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
abstract class FormulaComponent(eventBus: MessageBus) extends APIComponent {

  type SR <: SensorReport

  override def preStart(): Unit = {
    subscribeSensorReport()
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: SR => compute(msg)
  } orElse default

  def subscribeSensorReport(): Unit
  def compute(sensorReport: SR): Unit
}

/**
 * Power units.
 *
 * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object PowerUnit extends Enumeration {
  type PowerUnit = Value

  val W, kW = Value
}

/**
 * PowerChannel channel and messages.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object PowerChannel extends Channel {
  import org.powerapi.core.ClockChannel.ClockTick
  import org.powerapi.core.{Message, Target}
  import org.powerapi.module.PowerUnit.PowerUnit

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
   * Publish a PowerReport in the event bus.
   */
  def publishPowerReport(muid: UUID, target: Target, power: Double, unit: PowerUnit, device: String, tick: ClockTick): MessageBus => Unit = {
    publish(PowerReport(powerReportMuid(muid), muid, target, power, unit, device, tick))
  }

  /**
   * External method used by the Reporter for interacting with the bus.
   */
  def subscribePowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    subscribe(powerReportMuid(muid))
  }

  def unsubscribePowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    unsubscribe(powerReportMuid(muid))
  }

  private def powerReportMuid(muid: UUID): String  = {
    s"power:$muid"
  }
}
