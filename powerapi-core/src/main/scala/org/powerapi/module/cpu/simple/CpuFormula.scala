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

import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.module.FormulaComponent
import org.powerapi.module.cpu.UsageMetricsChannel.{subscribeSimpleUsageReport, UsageReport}
import org.powerapi.module.PowerChannel.publishRawPowerReport

/**
 * Implements a CpuFormula by making the ratio between maximum CPU power (obtained by multiplying
 * its Thermal Design Power (TDP) value by a specific factor) and the process CPU usage.
 *
 * @see http://en.wikipedia.org/wiki/Thermal_design_power
 *
 * @author <a href="mailto:aurelien.bourdon@gmail.com">Aurélien Bourdon</a
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class CpuFormula(eventBus: MessageBus, tdp: Double, tdpFactor: Double) extends FormulaComponent[UsageReport](eventBus) {
  def subscribeSensorReport(): Unit = {
    subscribeSimpleUsageReport(eventBus)(self)
  }

  def compute(sensorReport: UsageReport): Unit = {
    lazy val power = ((tdp * tdpFactor) * sensorReport.targetRatio.ratio).W
    publishRawPowerReport(sensorReport.muid, sensorReport.target, power, "cpu", sensorReport.tick)(eventBus)
  }
}
