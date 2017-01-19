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
package org.powerapi.module.disk.simple

import java.util.UUID

import akka.actor.Actor

import org.powerapi.core.MonitorChannel.{MonitorTick, unsubscribeMonitorTick, subscribeMonitorTick}
import org.powerapi.core.{Disk, OSHelper, MessageBus}
import org.powerapi.core.target.{All, Process, Target}
import org.powerapi.module.Sensor
import org.powerapi.module.disk.UsageMetricsChannel.{TargetDiskUsageRatio,publishDiskUsageReport}

/**
  * Disk sensor component which collects data from `blkio` cgroup subsystem, available on most recent Linux platforms.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class DiskSimpleSensor(eventBus: MessageBus, muid: UUID, target: Target, osHelper: OSHelper, disks: Seq[Disk]) extends Sensor(eventBus, muid, target) {

  def init(): Unit = subscribeMonitorTick(muid, target)(eventBus)(self)

  def terminate(): Unit = {
    unsubscribeMonitorTick(muid, target)(eventBus)(self)

    if (target != All) {
      updateCGroups(osHelper.getProcesses(target), Set())
    }
  }

  def updateCGroups(oldP: Set[Process], newP: Set[Process]): Unit = {
    if (target != All) {
      oldP.diff(newP).foreach(process => {
        if(osHelper.existsCGroup("blkio", s"powerapi/${process.pid}")) {
          osHelper.deleteCGroup("blkio", s"powerapi/${process.pid}")
        }
      })
      newP.diff(oldP).foreach(process => {
        if (!osHelper.existsCGroup("blkio", s"powerapi/${process.pid}")) {
          osHelper.createCGroup("blkio", s"powerapi/${process.pid}")
          osHelper.attachToCGroup("blkio", s"powerapi/${process.pid}", s"${process.pid}")
        }
      })
    }
  }

  def currentBytes(target: Target): (Seq[Disk], Seq[Disk]) = {
    val globalBytes = osHelper.getGlobalDiskBytes(disks)
    val targetBytes = target match {
      case All => globalBytes
      case _ => osHelper.getTargetDiskBytes(disks, target)
    }

    (targetBytes, globalBytes)
  }

  def handler: Actor.Receive = {
    val initProcesses = {
      if (target != All) {
        val processes = osHelper.getProcesses(target)
        updateCGroups(Set(), processes)
        processes
      }
      else Set[Process]()
    }

    val initBytes = currentBytes(target)
    val initTargetBytes = initBytes._1
    val initGlobalBytes = initBytes._2

    sense(initTargetBytes, initGlobalBytes, initProcesses)
  }

  def sense(oldTargetBytes: Seq[Disk], oldGlobalBytes: Seq[Disk], oldProcesses: Set[Process]): Actor.Receive = {
    case msg: MonitorTick =>
      val newProcesses = {
        if (target != All) {
          val processes = osHelper.getProcesses(target)
          updateCGroups(oldProcesses, processes)
          processes
        }
        else Set[Process]()
      }

      val newBytes = currentBytes(target)
      val newTargetBytes = newBytes._1
      val newGlobalBytes = newBytes._2

      val usages = for (disk <- disks) yield {
        val targetDiff = newTargetBytes.find(_.name == disk.name).get - oldTargetBytes.find(_.name == disk.name).get
        val globalDiff = newGlobalBytes.find(_.name == disk.name).get - oldGlobalBytes.find(_.name == disk.name).get
        val readRatio = if (globalDiff.bytesRead > 0) targetDiff.bytesRead / globalDiff.bytesRead.toDouble else 0d
        val writeRatio = if (globalDiff.bytesWritten > 0) targetDiff.bytesWritten / globalDiff.bytesWritten.toDouble else 0d
        TargetDiskUsageRatio(disk.name, globalDiff.bytesRead, readRatio, globalDiff.bytesWritten, writeRatio)
      }

      publishDiskUsageReport(muid, target, usages, msg.tick)(eventBus)
      context.become(sense(newTargetBytes, newGlobalBytes, newProcesses) orElse sensorDefault)
  }
}
