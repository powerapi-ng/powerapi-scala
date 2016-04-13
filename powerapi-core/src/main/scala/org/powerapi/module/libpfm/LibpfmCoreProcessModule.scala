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

import scala.collection.BitSet
import scala.concurrent.duration.FiniteDuration

import akka.util.Timeout

import org.powerapi.PowerModule
import org.powerapi.core.{LinuxHelper, OSHelper}
import org.powerapi.module.libpfm.cycles.{LibpfmCoreCyclesFormula, LibpfmCoreCyclesFormulaConfiguration}

class LibpfmCoreProcessModule(osHelper: OSHelper, libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String], inDepth: Boolean,
                              cyclesThreadName: String, cyclesRefName: String, pModel: Map[Double, List[Double]], samplingInterval: FiniteDuration) extends PowerModule {

  val sensor = Some((classOf[LibpfmCoreProcessSensor], Seq[Any](osHelper, libpfmHelper, timeout, topology, configuration, events, inDepth)))
  val formula = Some((classOf[LibpfmCoreCyclesFormula], Seq[Any](cyclesThreadName, cyclesRefName, pModel, samplingInterval)))
}

object LibpfmCoreProcessModule {
  def apply(prefixConfig: Option[String] = None, libpfmHelper: LibpfmHelper): LibpfmCoreProcessModule = {
    val linuxHelper = new LinuxHelper

    val coreProcessSensorConf = new LibpfmCoreProcessSensorConfiguration(prefixConfig)
    val coreCyclesFormulaConf = new LibpfmCoreCyclesFormulaConfiguration(prefixConfig)

    new LibpfmCoreProcessModule(linuxHelper, libpfmHelper, coreProcessSensorConf.timeout, coreProcessSensorConf.topology, coreProcessSensorConf.configuration, coreProcessSensorConf.events,
      coreProcessSensorConf.inDepth, coreCyclesFormulaConf.cyclesThreadName, coreCyclesFormulaConf.cyclesRefName, coreCyclesFormulaConf.formulae, coreCyclesFormulaConf.samplingInterval)
  }
}
