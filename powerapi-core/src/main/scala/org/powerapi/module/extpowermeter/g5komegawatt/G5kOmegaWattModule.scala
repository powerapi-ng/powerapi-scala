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
package org.powerapi.module.extpowermeter.g5komegawatt

import org.powerapi.PowerModule
import org.powerapi.core.power.Power
import org.powerapi.core.{ExternalPMeter, LinuxHelper, OSHelper}
import org.powerapi.module.extpowermeter.ExtPowerMeterFormulaConfiguration

class G5kOmegaWattModule(osHelper: OSHelper, pMeter: ExternalPMeter, idlePower: Power) extends PowerModule {
  val sensor = Some((classOf[G5kOmegaWattSensor], Seq(osHelper, pMeter, idlePower)))
  val formula = Some((classOf[G5kOmegaWattFormula], Seq()))
}

object G5kOmegaWattModule extends ExtPowerMeterFormulaConfiguration {

  def apply(prefixConfig: Option[String] = None): G5kOmegaWattModule = {
    val conf = new G5kOmegaWattPMeterConfiguration(prefixConfig)
    val linuxHelper = new LinuxHelper

    new G5kOmegaWattModule(linuxHelper, new G5kOmegawattPMeter(conf.probe, conf.interval), idlePower)
  }
}
