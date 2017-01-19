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
package org.powerapi.module.disk.simple

import org.powerapi.PowerModule
import org.powerapi.core.{Disk, LinuxHelper, OSHelper}

import scala.concurrent.duration.FiniteDuration

class DiskSimpleModule(osHelper: OSHelper, disks: Seq[Disk], interval: FiniteDuration, formulae: Map[String, Map[String, Seq[PieceWiseFunction]]]) extends PowerModule {
  val sensor = Some((classOf[DiskSimpleSensor], Seq(osHelper, disks)))
  val formula = Some((classOf[DiskSimpleFormula], Seq(interval, formulae)))
}

class DiskSimpleSensorModule(osHelper: OSHelper, disks: Seq[Disk]) extends PowerModule {
  val sensor = Some((classOf[DiskSimpleSensor], Seq(osHelper, disks)))
  val formula = None
}

object DiskSimpleModule {
  def apply(prefix: Option[String]): DiskSimpleModule = {
    val config = new DiskSimpleFormulaConfiguration(prefix)
    val linuxHelper = new LinuxHelper
    val disks = linuxHelper.getDiskInfo(config.formulae.keys.toSeq)
    linuxHelper.createCGroup("blkio", "powerapi")

    new DiskSimpleModule(linuxHelper, disks, config.interval, config.formulae)
  }
}

object DiskSimpleSensorModule {
  def apply(prefix: Option[String]): DiskSimpleSensorModule = {
    val config = new DiskSimpleSensorConfiguration(prefix)
    val linuxHelper = new LinuxHelper
    val disks = linuxHelper.getDiskInfo(config.ssds)
    linuxHelper.createCGroup("blkio", "powerapi")

    new DiskSimpleSensorModule(linuxHelper, disks)
  }
}
