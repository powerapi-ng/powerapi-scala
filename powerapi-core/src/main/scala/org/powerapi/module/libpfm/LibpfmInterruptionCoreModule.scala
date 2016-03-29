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
package org.powerapi.module.libpfm

import scala.concurrent.duration.FiniteDuration

import org.powerapi.PowerModule
import org.powerapi.module.libpfm.cycles.{LibpfmCoreCyclesFormulaConfiguration, LibpfmInterruptionCoreCyclesFormula}

class LibpfmInterruptionCoreModule(topology: Map[Int, Set[Int]], events: Set[String],
                                   cyclesThreadName: String, cyclesRefName: String, pModel: Map[Double, List[Double]], samplingInterval: FiniteDuration) extends PowerModule {

  val sensor = Some((classOf[LibpfmInterruptionCoreSensor], Seq(topology, events)))
  val formula = Some((classOf[LibpfmInterruptionCoreCyclesFormula], Seq(cyclesThreadName, cyclesRefName, pModel, samplingInterval)))
}

object LibpfmInterruptionCoreModule {
  def apply(prefixConfig: Option[String] = None): LibpfmInterruptionCoreModule = {
    val coreSensorConfig = new LibpfmCoreSensorConfiguration(prefixConfig)
    val coreCyclesFormulaConfig = new LibpfmCoreCyclesFormulaConfiguration(prefixConfig)

    new LibpfmInterruptionCoreModule(coreSensorConfig.topology, coreSensorConfig.events,
      coreCyclesFormulaConfig.cyclesThreadName, coreCyclesFormulaConfig.cyclesRefName,
      coreCyclesFormulaConfig.formulae, coreCyclesFormulaConfig.samplingInterval)
  }
}
