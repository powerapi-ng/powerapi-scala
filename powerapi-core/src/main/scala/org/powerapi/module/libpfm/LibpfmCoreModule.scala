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
import org.powerapi.module.libpfm.cycles.{LibpfmCoreCyclesFormula, LibpfmCoreCyclesFormulaConfiguration}

class LibpfmCoreModule(libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String],
                       cyclesThreadName: String, cyclesRefName: String, pModel: Map[Double, List[Double]], samplingInterval: FiniteDuration) extends PowerModule {

  val sensor = Some((classOf[LibpfmCoreSensor], Seq[Any](libpfmHelper, timeout, topology, configuration, events)))
  val formula = Some((classOf[LibpfmCoreCyclesFormula], Seq[Any](cyclesThreadName, cyclesRefName, pModel, samplingInterval)))
}

object LibpfmCoreModule {
  def apply(prefixConfig: Option[String] = None, libpfmHelper: LibpfmHelper): LibpfmCoreModule = {
    val coreSensorConfig = new LibpfmCoreSensorConfiguration(prefixConfig)
    val coreCyclesFormulaConfig = new LibpfmCoreCyclesFormulaConfiguration(prefixConfig)

    new LibpfmCoreModule(libpfmHelper, coreSensorConfig.timeout, coreSensorConfig.topology, coreSensorConfig.configuration,
      coreSensorConfig.events, coreCyclesFormulaConfig.cyclesThreadName, coreCyclesFormulaConfig.cyclesRefName,
      coreCyclesFormulaConfig.formulae, coreCyclesFormulaConfig.samplingInterval)
  }
}

class LibpfmCoreSensorModule(libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String]) extends PowerModule {
  val sensor = Some((classOf[LibpfmCoreSensor], Seq(libpfmHelper, timeout, topology, configuration, events)))
  val formula = None
}

object LibpfmCoreSensorModule {
  def apply(prefixConfig: Option[String] = None, libpfmHelper: LibpfmHelper): LibpfmCoreSensorModule = {
    val coreSensorConfig = new LibpfmCoreSensorConfiguration(prefixConfig)

    new LibpfmCoreSensorModule(libpfmHelper, coreSensorConfig.timeout, coreSensorConfig.topology, coreSensorConfig.configuration, coreSensorConfig.events)
  }

  def apply(prefixConfig: Option[String], libpfmHelper: LibpfmHelper, events: Set[String]): LibpfmCoreSensorModule = {
    val coreSensorConfig = new LibpfmCoreSensorConfiguration(prefixConfig)

    new LibpfmCoreSensorModule(libpfmHelper, coreSensorConfig.timeout, coreSensorConfig.topology, coreSensorConfig.configuration, events)
  }
}
