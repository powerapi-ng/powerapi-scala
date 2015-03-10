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
package org.powerapi.module.rapl

import org.powerapi.core.MessageBus
import org.powerapi.module.FormulaComponent
import org.powerapi.module.rapl.RAPLChannel.{RAPLPower, subscribeRAPLPower}
import org.powerapi.module.PowerChannel.publishRawPowerReport


/**
 * Implements a CpuFormula by making the ratio between current CPU power (obtained by collecting
 * data from RAPL registers) and the process CPU usage.
 *
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
class RAPLFormula(eventBus: MessageBus) extends FormulaComponent[RAPLPower](eventBus) {
  def subscribeSensorReport(): Unit = {
    subscribeRAPLPower(eventBus)(self)
  }

  def compute(sensorReport: RAPLPower): Unit = {
    lazy val power = sensorReport.power * sensorReport.targetRatio.ratio
    publishRawPowerReport(sensorReport.muid, sensorReport.target, power, "cpu", sensorReport.tick)(eventBus)
  }
}
