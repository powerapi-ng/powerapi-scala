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
package org.powerapi.module.powerspy

import org.powerapi.configuration.IdlePowerConfiguration
import org.powerapi.core.MessageBus
import org.powerapi.module.FormulaComponent
import org.powerapi.module.powerspy.PSpyMetricsChannel.PSpyDataReport

class PowerSpyFormula(eventBus: MessageBus) extends FormulaComponent[PSpyDataReport](eventBus) with IdlePowerConfiguration with Configuration {
  import org.powerapi.module.PowerChannel.publishPowerReport
  import org.powerapi.module.PowerUnit
  import org.powerapi.module.powerspy.PSpyMetricsChannel.{PSpyDataReport, PSpyAllDataReport, PSpyRatioDataReport, subscribePSpyAllDataReport,subscribePSpyRatioDataReport}

  def subscribeSensorReport(): Unit = {
    subscribePSpyAllDataReport(eventBus)(self)
    subscribePSpyRatioDataReport(eventBus)(self)
  }

  def compute(sensorReport: PSpyDataReport): Unit = {
    lazy val power: Double = sensorReport match {
      case msg: PSpyAllDataReport => msg.rms * msg.uScale * msg.iScale
      case msg: PSpyRatioDataReport => (msg.rms * msg.uScale * msg.iScale - idlePower) * msg.targetRatio.ratio
    }

    publishPowerReport(sensorReport.muid, sensorReport.target, power, PowerUnit.W, "powerspy", sensorReport.tick)(eventBus)
  }
}
