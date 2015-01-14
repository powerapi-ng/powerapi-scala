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
package org.powerapi.module.cpu.simple

import org.powerapi.core.MessageBus
import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
import org.powerapi.module.{PowerChannel, FormulaComponent}
import org.powerapi.module.cpu.UsageMetricsChannel

/**
 * CPU formula configuration.
 *
 * @author Aurélien Bourdon <aurelien.bourdon@gmail.com>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
trait FormulaConfiguration extends org.powerapi.core.Configuration {
  import org.powerapi.core.ConfigValue

  /**
   * CPU Thermal Design Power (TDP) value.
   *
   * @see http://en.wikipedia.org/wiki/Thermal_design_power
   */
  lazy val tdp = load { _.getInt("powerapi.cpu.tdp") } match {
    case ConfigValue(value) => value
    case _ => 0
  }

  /**
   * CPU Thermal Design Power (TDP) factor.
   *
   * @see [1], JouleSort: A Balanced Energy-Efﬁciency Benchmark, by Rivoire et al.
   */
  lazy val tdpFactor = load { _.getDouble("powerapi.cpu.tdp-factor") } match {
    case ConfigValue(value) => value
    case _ => 0.7
  }
}

/**
 * Implements a CpuFormula by making the ratio between maximum CPU power (obtained by multiplying
 * its Thermal Design Power (TDP) value by a specific factor) and the process CPU usage.
 *
 * @see http://en.wikipedia.org/wiki/Thermal_design_power
 *
 * @author Aurélien Bourdon <aurelien.bourdon@gmail.com>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class CpuFormula(eventBus: MessageBus) extends FormulaComponent[UsageReport](eventBus) with FormulaConfiguration {
  import UsageMetricsChannel.subscribeSimpleUsageReport
  import PowerChannel.publishPowerReport
  import org.powerapi.module.PowerUnit

  def subscribeSensorReport(): Unit = {
    subscribeSimpleUsageReport(eventBus)(self)
  }

  def compute(sensorReport: UsageReport): Unit = {
    lazy val power = (tdp * tdpFactor) * sensorReport.targetRatio.ratio
    publishPowerReport(sensorReport.muid, sensorReport.target, power, PowerUnit.W, "cpu", sensorReport.tick)(eventBus)
  }
}
