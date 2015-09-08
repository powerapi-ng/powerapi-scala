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
package org.powerapi.module.cpu.simple

import org.powerapi.core.{MessageBus, OSHelper}
import org.powerapi.module.SensorComponent
import org.powerapi.core.MonitorChannel.MonitorTick
import org.powerapi.core.target.{All, Application, Process, TargetUsageRatio}
import org.powerapi.module.cpu.UsageMetricsChannel.publishUsageReport
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll}
import scala.reflect.ClassTag

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

  def targetCpuUsageRatio(monitorTick: MonitorTick): TargetUsageRatio = {
    val processClaz = implicitly[ClassTag[Process]].runtimeClass
    val appClaz = implicitly[ClassTag[Application]].runtimeClass

    monitorTick.target match {
      case target if processClaz.isInstance(target) || appClaz.isInstance(target) => {
        osHelper.getTargetCpuPercent(monitorTick.muid, target)
      }
      case All => osHelper.getGlobalCpuPercent(monitorTick.muid)
      case _ => log.warning("Only Process, Application, or All targets can be used with this Sensor"); TargetUsageRatio(0.0)
    }
  }

  def sense(monitorTick: MonitorTick): Unit = {
    publishUsageReport(monitorTick.muid, monitorTick.target, targetCpuUsageRatio(monitorTick), monitorTick.tick)(eventBus)
  }

  def monitorStopped(msg: MonitorStop): Unit = {}

  def monitorAllStopped(msg: MonitorStopAll): Unit = {}
}
