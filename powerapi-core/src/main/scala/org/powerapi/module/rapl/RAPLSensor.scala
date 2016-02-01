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
package org.powerapi.module.rapl

import org.powerapi.core.{MessageBus, OSHelper}
import org.powerapi.module.SensorComponent
import scala.reflect.ClassTag
import org.powerapi.core.power.Power
import org.powerapi.core.target.{Application, All, Container, Process, TargetUsageRatio}
import org.powerapi.core.MonitorChannel.MonitorTick
import org.powerapi.module.{Cache, CacheKey}
import org.powerapi.module.rapl.RAPLChannel.publishRAPLPower
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll}

/**
 * RAPL sensor component that collects data from RAPL registers (MSR).
 *
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
class RAPLSensor(eventBus: MessageBus, osHelper: OSHelper, raplHelper: RAPLHelper) extends SensorComponent(eventBus) {
  lazy val cpuTimesCache = new Cache[(Long, Long)]
  lazy val powers = new Cache[Double]

  def targetCpuUsageRatio(monitorTick: MonitorTick): TargetUsageRatio = {
    val key = CacheKey(monitorTick.muid, monitorTick.target)

    lazy val activeCpuTime = osHelper.getGlobalCpuTime.activeTime

    val processClaz = implicitly[ClassTag[Process]].runtimeClass
    val appClaz = implicitly[ClassTag[Application]].runtimeClass
    val containerClaz = implicitly[ClassTag[Container]].runtimeClass

    lazy val now = monitorTick.target match {
      case target if processClaz.isInstance(target) || appClaz.isInstance(target) || containerClaz.isInstance(target) => {
        lazy val targetCpuTime = osHelper.getTargetCpuTime(target) match {
          case Some(time) => time
          case _ => log.warning("Only Process, Application, Container or All targets can be used with this Sensor"); 0l
        }

        (targetCpuTime, activeCpuTime)
      }
      case All => (activeCpuTime, activeCpuTime)
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

  def getEnergy(monitorTick: MonitorTick): Double = {
    val key = CacheKey(monitorTick.muid, monitorTick.target)
    val now = raplHelper.getRAPLEnergy
    val old = powers(key)(now)
    powers(key) = now
    now - old
  }

  def sense(monitorTick: MonitorTick): Unit = {
    publishRAPLPower(monitorTick.muid,
                     monitorTick.target,
                     Power.fromJoule(getEnergy(monitorTick), monitorTick.tick.frequency),
                     targetCpuUsageRatio(monitorTick),
                     monitorTick.tick)(eventBus)
  }
  
  def monitorStopped(msg: MonitorStop): Unit = {
    cpuTimesCache -= msg.muid
  }

  def monitorAllStopped(msg: MonitorStopAll): Unit = {
    cpuTimesCache.clear()
  }
}
