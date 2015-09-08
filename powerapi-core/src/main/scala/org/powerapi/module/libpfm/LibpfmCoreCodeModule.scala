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
import org.powerapi.module.libpfm.cycles.{LibpfmCoreCyclesFormulaConfiguration, LibpfmCoreCyclesFormula}
import scala.concurrent.duration.FiniteDuration

class LibpfmCoreCodeModule(libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], events: Set[String], controlFlowServerPath: String, fdFlowServerPath: String, ancillaryHelper: AncillaryHelper,
                             cyclesThreadName: String, cyclesRefName: String, formulae: Map[Double, List[Double]], samplingInterval: FiniteDuration) extends PowerModule {

  lazy val underlyingSensorsClasses  = Seq((classOf[LibpfmCoreCodeSensor], Seq(libpfmHelper, timeout, topology, events, controlFlowServerPath, fdFlowServerPath, ancillaryHelper)))
  lazy val underlyingFormulaeClasses = Seq((classOf[LibpfmCoreCyclesFormula], Seq(cyclesThreadName, cyclesRefName, formulae, samplingInterval)))
}

object LibpfmCoreCodeModule {
  def apply(prefixConfig: Option[String] = None, libpfmHelper: LibpfmHelper, ancillaryHelper: AncillaryHelper): LibpfmCoreCodeModule = {
    val coreMethodSensorConf = new LibpfmCoreCodeSensorConfiguration(prefixConfig)
    val fdUnixServerConf = new FDUnixServerSocketConfiguration(prefixConfig)
    val coreCyclesFormulaConf = new LibpfmCoreCyclesFormulaConfiguration(prefixConfig)

    new LibpfmCoreCodeModule(libpfmHelper, coreMethodSensorConf.timeout, coreMethodSensorConf.topology, coreMethodSensorConf.events,
      fdUnixServerConf.controlFlowServerPath, fdUnixServerConf.fdFlowServerPath, ancillaryHelper, coreCyclesFormulaConf.cyclesThreadName, coreCyclesFormulaConf.cyclesRefName, coreCyclesFormulaConf.formulae, coreCyclesFormulaConf.samplingInterval)
  }
}
