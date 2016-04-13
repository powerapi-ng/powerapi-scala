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
package org.powerapi.module.cpu.simple

import java.util.UUID

import akka.actor.Actor

import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick, unsubscribeMonitorTick}
import org.powerapi.core.target.{All, Target, TargetUsageRatio}
import org.powerapi.core.{MessageBus, OSHelper}
import org.powerapi.module.Sensor
import org.powerapi.module.cpu.UsageMetricsChannel.publishUsageReport

/**
  * CPU sensor component that collects data from /proc directory, presents on Linux platform.
  *
  * @see http://www.kernel.org/doc/man-pages/online/pages/man5/proc.5.html
  * @author <a href="mailto:aurelien.bourdon@gmail.com">Aurélien Bourdon</a
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class CpuSimpleSensor(eventBus: MessageBus, muid: UUID, target: Target, osHelper: OSHelper) extends Sensor(eventBus, muid, target) {

  def init(): Unit = subscribeMonitorTick(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeMonitorTick(muid, target)(eventBus)(self)

  def currentTimes(target: Target): (Long, Long) = {
    val globalTimes = osHelper.getGlobalCpuTimes
    val allTime = globalTimes.idleTime + globalTimes.activeTime
    val targetTime = target match {
      case All => globalTimes.activeTime
      case _ => osHelper.getTargetCpuTime(target)
    }

    (targetTime, allTime)
  }

  def usageRatio(oldT: Long, newT: Long, oldG: Long, newG: Long): TargetUsageRatio = {
    val targetTime = if (newT - oldT > 0) newT - oldT else 0
    val globalTime = if (newG - oldG > 0) newG - oldG else 0

    if (globalTime > 0) {
      TargetUsageRatio(targetTime / globalTime.toDouble)
    }
    else
      TargetUsageRatio(0)
  }

  def handler: Actor.Receive = {
    val initTimes = currentTimes(target)
    sense(initTimes._1, initTimes._2)
  }

  def sense(oldTargetTime: Long, oldGlobalTime: Long): Actor.Receive = {
    case msg: MonitorTick =>
      val newTimes = currentTimes(target)
      publishUsageReport(muid, target, usageRatio(oldTargetTime, newTimes._1, oldGlobalTime, newTimes._2), msg.tick)(eventBus)
      context.become(sense(newTimes._1, newTimes._2) orElse sensorDefault)
  }
}
