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
import org.powerapi.core.{LinuxHelper, OSHelper}

import scala.collection.BitSet
import scala.concurrent.duration.FiniteDuration

/**
 * This module uses a general formula for representing the overall CPU's power consumption.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmProcessModule(osHelper: OSHelper, libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String], inDepth: Boolean,
                          formula: Map[String, Double], samplingInterval: FiniteDuration) extends PowerModule {

  lazy val underlyingSensorsClasses  = Seq((classOf[LibpfmCoreProcessSensor], Seq(osHelper, libpfmHelper, timeout, topology, configuration, events, inDepth)))
  lazy val underlyingFormulaeClasses = Seq((classOf[LibpfmFormula], Seq(formula, samplingInterval)))
}

object LibpfmProcessModule {
  def apply(prefixConfig: Option[String] = None, libpfmHelper: LibpfmHelper): LibpfmProcessModule = {
    val linuxHelper = new LinuxHelper

    val coreProcessSensorConf = new LibpfmCoreProcessSensorConfiguration(prefixConfig)
    val formulaConfig = new LibpfmFormulaConfiguration(prefixConfig)

    new LibpfmProcessModule(linuxHelper, libpfmHelper, coreProcessSensorConf.timeout, coreProcessSensorConf.topology, coreProcessSensorConf.configuration, coreProcessSensorConf.events,
      coreProcessSensorConf.inDepth, formulaConfig.formula, formulaConfig.samplingInterval)
  }
}
