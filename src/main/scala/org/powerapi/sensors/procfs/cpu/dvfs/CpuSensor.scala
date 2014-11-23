/**
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

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */

package org.powerapi.sensors.procfs.cpu.dvfs

import org.powerapi.core.{ConfigValue, MessageBus, OSHelper}

/**
 * CPU sensor configuration.
 *
 * @author abourdon
 * @author mcolmant
 */
trait Configuration extends org.powerapi.core.Configuration {
  /**
   * OS cores number (can be logical).
   */
  lazy val cores = load { _.getString("powerapi.hardware.cores") } match {
    case ConfigValue(nbCores) => nbCores
    case _ => "0"
  }

  /**
   * Time in state file, giving information about how many time CPU spent under each frequency.
   */
  lazy val timeInStatePath = load { _.getString("powerapi.sysfs.time-in-states-path") } match {
    case ConfigValue(path) => path
    case _ => "/sys/devices/system/cpu/cpu%?index/cpufreq/stats/time_in_state"
  }
}

/**
 * CPU sensor component that collects data from a /proc and /sys directories
 * which are typically presents under a Linux platform.
 *
 * @see http://www.kernel.org/doc/man-pages/online/pages/man5/proc.5.html
 *
 * @author abourdon
 * @author mcolmant
 */
class CpuSensor(eventBus: MessageBus, osHelper: OSHelper) extends org.powerapi.sensors.procfs.cpu.simple.CpuSensor(eventBus, osHelper) with Configuration {
  /**
   * Delegate class to deal with time spent within each CPU frequencies.
   */
  class Frequencies {
    // time_in_state line format: frequency time
    private val TimeInStateFormat = """(\d+)\s+(\d+)""".r

    /*def timeInStates(): Map[Int, Long] = {
      for(core <- cores) {

      }
    }*/
  }


}
