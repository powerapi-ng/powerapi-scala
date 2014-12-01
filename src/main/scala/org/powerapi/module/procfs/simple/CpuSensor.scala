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
package org.powerapi.module.procfs.simple

import org.powerapi.core.{MessageBus, OSHelper}
import org.powerapi.module.SensorComponent
import org.powerapi.module.procfs.ProcMetricsChannel

/**
 * CPU sensor component that collects data from a /proc and /sys directories
 * which are typically presents under a Linux platform.
 *
 * @see http://www.kernel.org/doc/man-pages/online/pages/man5/proc.5.html
 *
 * @author Aurélien Bourdon <aurelien.bourdon@gmail.com>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class CpuSensor(eventBus: MessageBus, osHelper: OSHelper) extends SensorComponent(eventBus) {
  import org.powerapi.core.MonitorChannel.MonitorTick
  import ProcMetricsChannel.publishUsageReport

  /**
   * Delegate class collecting time information contained into both globalStatPath and processStatPath files
   * and providing the target CPU ratio usage.
   */
  class TargetRatio {
    import org.powerapi.core.{All, Application, Process}
    import ProcMetricsChannel.CacheKey

    /**
     * Internal cache, used to get the diff between two ClockTick.
     */
    lazy val cache = collection.mutable.Map[CacheKey, (Long, Long)]()

    def refreshCache(key: CacheKey, now: (Long, Long)): Unit = {
      cache += (key -> now)
    }

    def handleProcessTarget(process: Process): (Long, Long) = {
      lazy val processTime: Long = osHelper.getProcessCpuTime(process) match {
        case Some(value) => value
        case _ => 0l
      }
      lazy val globalTime: Long = osHelper.getGlobalCpuTime() match {
        case Some(value) => value
        case _ => 0l
      }

      (processTime, globalTime)
    }

    def handleApplicationTarget(application: Application): (Long, Long) = {
      lazy val processTime: Long = osHelper.getProcesses(application).foldLeft(0: Long) {
        (acc, process: Process) => {
          osHelper.getProcessCpuTime(process) match {
            case Some(value) => acc + value
            case _ => acc
          }
        }
      }
      lazy val globalTime: Long = osHelper.getGlobalCpuTime() match {
        case Some(value) => value
        case _ => 0l
      }
      (processTime, globalTime)
    }

    def handleMonitorTick(tick: MonitorTick): ProcMetricsChannel.TargetUsageRatio = {
      val now = tick.target match {
        case process: Process => handleProcessTarget(process)
        case application: Application => handleApplicationTarget(application)
        case All => (0l, 0l)
      }

      val key = CacheKey(tick.muid, tick.target)
      val old = cache.getOrElse(key, now)
      refreshCache(key, now)

      val globalDiff = now._2 - old._2
      if (globalDiff <= 0) {
        ProcMetricsChannel.TargetUsageRatio(0)
      }
      else {
        ProcMetricsChannel.TargetUsageRatio((now._1 - old._1).doubleValue / globalDiff)
      }
    }
  }

  lazy val targetRatio = new TargetRatio

  def sense(monitorTick: MonitorTick): Unit = {
    publishUsageReport(monitorTick.muid, monitorTick.target, targetRatio.handleMonitorTick(monitorTick), monitorTick.tick)(eventBus)
  }
}
