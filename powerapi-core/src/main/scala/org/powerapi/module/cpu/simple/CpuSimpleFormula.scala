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
package org.powerapi.module.cpu.simple

import java.util.UUID

import akka.actor.Actor
import akka.event.LoggingReceive

import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.cpu.UsageMetricsChannel.{SimpleUsageReport, subscribeSimpleUsageReport, unsubscribeSimpleUsageReport}

/**
  * Implements a CpuFormula by making the ratio between maximum CPU power (obtained by multiplying
  * its Thermal Design Power (TDP) value by a tuned factor) and the target's CPU usage.
  *
  * @see http://en.wikipedia.org/wiki/Thermal_design_power
  * @author <a href="mailto:aurelien.bourdon@gmail.com">Aurélien Bourdon</a
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class CpuSimpleFormula(eventBus: MessageBus, muid: UUID, target: Target, tdp: Double, tdpFactor: Double) extends Formula(eventBus, muid, target) {

  def init(): Unit = subscribeSimpleUsageReport(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeSimpleUsageReport(muid, target)(eventBus)(self)

  def handler: Actor.Receive = LoggingReceive {
    case msg: SimpleUsageReport =>
      val power = ((tdp * tdpFactor) * msg.targetRatio.ratio).W
      publishRawPowerReport(msg.muid, msg.target, power, "cpu", msg.tick)(eventBus)
  }
}
