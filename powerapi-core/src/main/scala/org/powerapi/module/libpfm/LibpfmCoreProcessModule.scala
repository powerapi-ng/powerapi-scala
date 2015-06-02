/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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

import akka.util.Timeout
import org.powerapi.PowerModule
import org.powerapi.core.{OSHelper, LinuxHelper}
import org.powerapi.module.libpfm.cycles.{LibpfmCoreCyclesFormulaConfiguration, LibpfmCoreCyclesFormula}
import scala.collection.BitSet
import scala.concurrent.duration.FiniteDuration

class LibpfmCoreProcessModule(osHelper: OSHelper, libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String], inDepth: Boolean,
                              cyclesThreadName: String, cyclesRefName: String, formulae: Map[Double, List[Double]], samplingInterval: FiniteDuration) extends PowerModule {

  lazy val underlyingSensorsClasses  = Seq((classOf[LibpfmCoreProcessSensor], Seq(osHelper, libpfmHelper, timeout, topology, configuration, events, inDepth)))
  lazy val underlyingFormulaeClasses = Seq((classOf[LibpfmCoreCyclesFormula], Seq(cyclesThreadName, cyclesRefName, formulae, samplingInterval)))
}

object LibpfmCoreProcessModule {
  def apply(configPrefix: Option[String] = None, libpfmHelper: LibpfmHelper): LibpfmCoreProcessModule = {
    val linuxHelper = new LinuxHelper

    val coreProcessSensorConf = new LibpfmCoreProcessSensorConfiguration(configPrefix)
    val coreCyclesFormulaConf = new LibpfmCoreCyclesFormulaConfiguration(configPrefix)

    new LibpfmCoreProcessModule(linuxHelper, libpfmHelper, coreProcessSensorConf.timeout, coreProcessSensorConf.topology, coreProcessSensorConf.configuration, coreProcessSensorConf.events,
      coreProcessSensorConf.inDepth, coreCyclesFormulaConf.cyclesThreadName, coreCyclesFormulaConf.cyclesRefName, coreCyclesFormulaConf.formulae, coreCyclesFormulaConf.samplingInterval)
  }
}
