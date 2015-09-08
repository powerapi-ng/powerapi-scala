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
package org.powerapi.reporter

import java.util.UUID
import org.powerapi.PowerDisplay
import org.powerapi.core.power.Power
import org.powerapi.core.target.Target
import scalax.io.Resource

/**
 * Display power information into a given file.
 *
 * @author Aurélien Bourdon <aurelien.bourdon@gmail.com>
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class FileDisplay(filepath: String) extends PowerDisplay  {

  lazy val output = Resource.fromFile(filepath)

  def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power) {
    output.append(s"muid=$muid;timestamp=$timestamp;targets=${targets.mkString(",")};devices=${devices.mkString(",")};power=${power.toWatts}\n")
  }

  override def equals(that: Any): Boolean = that match {
    case that: FileDisplay => this.hashCode == that.hashCode
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(filepath)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
