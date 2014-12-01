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
package org.powerapi.module.procfs.dvfs

import org.powerapi.core.{MessageBus, OSHelper}
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
class CpuSensor(eventBus: MessageBus, osHelper: OSHelper) extends org.powerapi.module.procfs.simple.CpuSensor(eventBus, osHelper) {
  import org.powerapi.core.MonitorChannel.MonitorTick
  import ProcMetricsChannel.publishUsageReport

  /**
   * Delegate class to deal with time spent within each CPU frequencies.
   */
  class Frequencies {
    import org.powerapi.core.TimeInStates
    import ProcMetricsChannel.CacheKey

    lazy val cache = collection.mutable.Map[CacheKey, TimeInStates]()

    def refreshCache(key: CacheKey, now: TimeInStates): Unit = {
      cache += (key -> now)
    }

    def handleMonitorTick(tick: MonitorTick): TimeInStates = {
      val now = osHelper.getTimeInStates()
      val key = CacheKey(tick.muid, tick.target)
      val old = cache.getOrElse(key, now)
      refreshCache(key, now)
      now - old
    }
  }

  lazy val frequencies = new Frequencies

  override def sense(monitorTick: MonitorTick): Unit = {
    publishUsageReport(monitorTick.muid, monitorTick.target, targetRatio.handleMonitorTick(monitorTick), frequencies.handleMonitorTick(monitorTick), monitorTick.tick)(eventBus)
  }
}
