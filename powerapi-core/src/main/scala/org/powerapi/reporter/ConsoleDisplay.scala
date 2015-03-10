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
package org.powerapi.reporter

import org.powerapi.PowerDisplay
import org.powerapi.core.power.Power
import org.powerapi.core.target.Target

/**
 * Display power information into the console.
 *
 * @author Aurélien Bourdon <aurelien@bourdon@gmail.com>
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class ConsoleDisplay extends PowerDisplay {

  def display(timestamp: Long, targets: Set[Target], devices: Set[String], power: Power) {
    println(s"timestamp=$timestamp;target=${targets.mkString(";")};devices=${devices.mkString(",")};power=${power.toWatts}")
  }
}

