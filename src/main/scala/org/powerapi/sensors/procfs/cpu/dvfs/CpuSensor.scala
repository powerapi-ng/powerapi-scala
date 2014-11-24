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

import org.powerapi.core.{MessageBus, OSHelper}

/**
 * CPU sensor configuration.
 *
 * @author Aurélien Bourdon <aurelien@bourdon@gmail.com>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
trait Configuration extends org.powerapi.core.Configuration {
  import org.powerapi.core.ConfigValue

  /**
   * OS cores number (can be logical).
   */
  lazy val cores = load { _.getInt("powerapi.hardware.cores") } match {
    case ConfigValue(nbCores) => nbCores
    case _ => 0
  }

  /**
   * Time in state file, giving information about how many time CPU spent under each frequency.
   */
  lazy val timeInStatePath = load { _.getString("powerapi.sysfs.timeinstates-path") } match {
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
 * @author Aurélien Bourdon <aurelien@bourdon@gmail.com>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class CpuSensor(eventBus: MessageBus, osHelper: OSHelper) extends org.powerapi.sensors.procfs.cpu.simple.CpuSensor(eventBus, osHelper) with Configuration {
  import org.powerapi.core.MonitorChannel.MonitorTick
  import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.publishCpuReport

  /**
   * Delegate class to deal with time spent within each CPU frequencies.
   */
  class Frequencies {
    import java.io.IOException
    import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.{CacheKey, TimeInStates}
    import org.powerapi.sensors.procfs.cpu.FileControl.using
    import scala.io.Source

    // time_in_state line format: frequency time
    private val TimeInStateFormat = """(\d+)\s+(\d+)""".r
    lazy val cache = collection.mutable.Map[CacheKey, TimeInStates]()

    def timeInStates(): Map[Int, Long] = {
      val result = collection.mutable.HashMap[Int, Long]()

      for(core <- 0 until cores) {
        try {
          using(Source.fromFile(timeInStatePath.replace("%?index", s"$core")))(source => {
            log.debug("using {} as a sysfs timeinstates path", timeInStatePath)

            for(line <- source.getLines) {
              line match {
                case TimeInStateFormat(freq, t) => result += (freq.toInt -> (t.toLong + (result.getOrElse(freq.toInt, 0l))))
              }
            }
          })
        }
        catch {
          case ioe: IOException => log.warning("i/o exception: {}", ioe.getMessage);
        }
      }

      result.toMap[Int, Long]
    }

    def refreshCache(key: CacheKey, now: TimeInStates): Unit = {
      cache += (key -> now)
    }

    def handleMonitorTick(tick: MonitorTick): TimeInStates = {
      val now = TimeInStates(timeInStates)
      val key = CacheKey(tick.muid, tick.target)
      val old = cache.getOrElse(key, now)
      refreshCache(key, now)
      now - old
    }
  }

  lazy val frequencies = new Frequencies

  override def sense(monitorTick: MonitorTick): Unit = {
    publishCpuReport(monitorTick.muid, monitorTick.target, targetRatio.handleMonitorTick(monitorTick), frequencies.handleMonitorTick(monitorTick), monitorTick.tick)(eventBus)
  }
}
