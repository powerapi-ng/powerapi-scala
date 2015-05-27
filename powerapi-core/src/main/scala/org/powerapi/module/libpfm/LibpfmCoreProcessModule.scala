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
package org.powerapi.module.libpfm

import akka.util.Timeout
import org.powerapi.PowerModule
import org.powerapi.core.LinuxHelper
import org.powerapi.module.libpfm.cycles.{LibpfmCoreCyclesFormulaConfiguration, LibpfmCoreCyclesFormula}
import scala.collection.BitSet
import scala.concurrent.duration.FiniteDuration

class LibpfmCoreProcessModule(libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String], inDepth: Boolean,
                              cyclesThreadName: String, cyclesRefName: String, formulae: Map[Double, List[Double]], samplingInterval: FiniteDuration) extends PowerModule {

  lazy val underlyingSensorsClasses  = Seq((classOf[LibpfmCoreProcessSensor], Seq(new LinuxHelper, libpfmHelper, timeout, topology, configuration, events, inDepth)))
  lazy val underlyingFormulaeClasses = Seq((classOf[LibpfmCoreCyclesFormula], Seq(cyclesThreadName, cyclesRefName, formulae, samplingInterval)))
}

object LibpfmCoreProcessModule extends LibpfmCoreProcessSensorConfiguration with LibpfmCoreCyclesFormulaConfiguration {
  lazy val libpfmHelper = new LibpfmHelper

  def apply(): LibpfmCoreProcessModule = {
    new LibpfmCoreProcessModule(libpfmHelper, timeout, topology, configuration, events, inDepth, cyclesThreadName, cyclesRefName, formulae, samplingInterval)
  }

  def apply(libpfmHelper: LibpfmHelper): LibpfmCoreProcessModule = {
    new LibpfmCoreProcessModule(libpfmHelper, timeout, topology, configuration, events, inDepth, cyclesThreadName, cyclesRefName, formulae, samplingInterval)
  }
}
