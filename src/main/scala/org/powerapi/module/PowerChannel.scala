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

import akka.actor.ActorRef
import org.powerapi.core.{MessageBus, Channel}

/**
 * Power units.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object PowerUnit extends Enumeration {

  case class PowerUnit(name: String, description: String) extends Val

  val W = PowerUnit("W", "Watts")
  val kW = PowerUnit("kW", "KiloWatts")
}

/**
 * PowerChannel channel and messages.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object PowerChannel extends Channel {
  import org.powerapi.core.ClockChannel.ClockTick
  import org.powerapi.core.Message
  import org.powerapi.core.target.Target
  import org.powerapi.module.PowerUnit.PowerUnit

  type M = PowerReport

  /**
   * PowerReport is represented as a dedicated type of message.
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
