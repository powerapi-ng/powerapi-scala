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
package org.powerapi.module.cpu.simple

import org.powerapi.core.{MessageBus, OSHelper}
import org.powerapi.module.SensorComponent

/**
 * CPU sensor component that collects data from a /proc and /sys directories
 * which are typically presents under a Linux platform.
 *
 * @see http://www.kernel.org/doc/man-pages/online/pages/man5/proc.5.html
 *
 * @author <a href="mailto:aurelien.bourdon@gmail.com">Aurélien Bourdon</a
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class CpuSensor(eventBus: MessageBus, osHelper: OSHelper) extends SensorComponent(eventBus) {
  import org.powerapi.core.GlobalCpuTime
  import org.powerapi.core.MonitorChannel.MonitorTick
  import org.powerapi.core.target.{All, Application, Process, TargetUsageRatio}
  import org.powerapi.module.{Cache, CacheKey}
  import org.powerapi.module.cpu.UsageMetricsChannel.publishUsageReport
  import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll}
  import scala.reflect.ClassTag

  lazy val cpuTimesCache = new Cache[(Long, Long)]

  def targetCpuUsageRatio(monitorTick: MonitorTick): TargetUsageRatio = {
    val key = CacheKey(monitorTick.muid, monitorTick.target)

    lazy val (globalCpuTime, activeCpuTime) = osHelper.getGlobalCpuTime match {
      case GlobalCpuTime(globalTime, activeTime) => (globalTime, activeTime)
    }

    val processClaz = implicitly[ClassTag[Process]].runtimeClass
    val appClaz = implicitly[ClassTag[Application]].runtimeClass

    lazy val now = monitorTick.target match {
      case target if processClaz.isInstance(target) || appClaz.isInstance(target) => {
        lazy val targetCpuTime = osHelper.getTargetCpuTime(target) match {
          case Some(time) => time
          case _ => 0l
        }

        (targetCpuTime, globalCpuTime)
      }
      case All => (activeCpuTime, globalCpuTime)
    }

    val old = cpuTimesCache(key)(now)
    val diffTimes = (now._1 - old._1, now._2 - old._2)

    diffTimes match {
      case diff: (Long, Long) => {
        if(old == now) {
          cpuTimesCache(key) = now
          TargetUsageRatio(0.0)
        }

        else if (diff._1 > 0 && diff._2 > 0 && diff._1 <= diff._2) {
          cpuTimesCache(key) = now
          TargetUsageRatio(diff._1.toDouble / diff._2)
        }

        else TargetUsageRatio(0.0)
      }
      case _ => TargetUsageRatio(0.0)
    }
  }

  def sense(monitorTick: MonitorTick): Unit = {
    publishUsageReport(monitorTick.muid, monitorTick.target, targetCpuUsageRatio(monitorTick), monitorTick.tick)(eventBus)
  }

  def monitorStopped(msg: MonitorStop): Unit = {
    cpuTimesCache -= msg.muid
  }

  def monitorAllStopped(msg: MonitorStopAll): Unit = {
    cpuTimesCache.clear()
  }
}
