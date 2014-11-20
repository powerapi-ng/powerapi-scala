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

package org.powerapi.sensors.procfs.cpu.simple

import java.io.IOException

import org.powerapi.core.MonitorChannel.MonitorTarget
import org.powerapi.core.{All, ConfigValue, MessageBus, Sensor}

import scala.io.Source

/**
 * CPU sensor configuration.
 *
 * @author abourdon
 * @author mcolmant
 */
trait Configuration extends org.powerapi.core.Configuration {
  /**
   * Global stat file, giving global information of the system itself.
   * Typically presents under /proc/stat.
   */
  lazy val globalStatPath = load { _.getString("powerapi.procfs.global-stat") } match {
    case ConfigValue(path) => path
    case _ => "/proc/stat"
  }

  /**
   * Process stat file, giving information about the process itself.
   * Typically presents under /proc/[pid]/stat.
   */
  lazy val processStatPath = load { _.getString("powerapi.procfs..process-stat") } match {
    case ConfigValue(path) if path.contains("$pid") => path
    case _ => "proc/$pid/stat"
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
class CpuSensor(eventBus: MessageBus) extends Sensor(eventBus) with Configuration {
  import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.publishCpuReport

  /**
   * Delegate class collecting time information contained into both globalStatPath and processStatPath files
   * and providing the target CPU percent usage.
   */
  class TargetPercent {
    import org.powerapi.core.{Application, Process}
    import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.TargetPercent

    private val GlobalStatFormat = """cpu\s+([\d\s]+)""".r

    /**
     * Internal cache, used to get the diff between two ClockTick.
     */
    private lazy val cache = collection.mutable.Map[MonitorTarget, (Long, Long)]()

    private def globalElapsedTime(): Long = {
      try {
        val bufferedSource = Source.fromFile(globalStatPath)
        log.debug("using {} as a procfs global stat path", globalStatPath)

        bufferedSource.getLines.toIndexedSeq(0) match {
          /**
           * Exclude all guest columns, they are already added in utime column.
           *
           * @see http://lxr.free-electrons.com/source/kernel/sched/cputime.c#L165
           */
          case GlobalStatFormat(times) => times.split("\\s").slice(0, 8).foldLeft(0: Long) {
            (acc, x) => (acc + x.toLong)
          }
          case _ => log.warning("unable to parse line from file {}", globalStatPath); 0l
        }
      }
      catch {
        case ioe: IOException => log.warning("i/o exception: {}", ioe.getMessage); 0l
      }
    }

    private def processElapsedTime(process: Process): Long = {
      try {
        val bufferedSource = Source.fromFile(processStatPath.replace("$pid", s"${process}.pid"))
        log.debug("using {} as a procfs process stat path", processStatPath)

        val statLine = bufferedSource.getLines.toIndexedSeq(0).split("\\s")
        // User time + System time
        statLine(13).toLong + statLine(14).toLong
      }
      catch {
        case ioe: IOException => log.warning("i/o exception: {}", ioe.getMessage); 0l
      }
    }

    private def refreshCache(monitorTarget: MonitorTarget, now: (Long, Long)): Unit = {
      cache += (monitorTarget -> now)
    }

    private def handleProcessTarget(process: Process): (Long, Long) = {
      (processElapsedTime(process), globalElapsedTime())
    }

    private def handleApplicationTarget(application: Application): (Long, Long) = {
      import org.powerapi.core.LinuxHelper.getProcesses

      lazy val elapsedTime = getProcesses(application).foldLeft(0: Long) {
        (acc, process: Process) => (acc + processElapsedTime(process))
      }

      (elapsedTime, globalElapsedTime())
    }

    def handleMonitorTarget(monitorTarget: MonitorTarget) = {
      val now = monitorTarget.target match {
        case process: Process => handleProcessTarget(process)
        case application: Application => handleApplicationTarget(application)
        case All => log.warning("target All is not handled by this sensor"); (0l, 0l)
      }
      val old = cache.getOrElse(monitorTarget, now)
      refreshCache(monitorTarget, now)

      val globalDiff = now._2 - old._2

      if(globalDiff <= 0) {
        TargetPercent(0)
      }
      else {
        TargetPercent((now._1 - old._1).doubleValue / globalDiff)
      }
    }
  }

  lazy val targetPercent = new TargetPercent

  def process(monitorTarget: MonitorTarget): Unit = {
    publishCpuReport(monitorTarget.muid, monitorTarget.target, targetPercent.handleMonitorTarget(monitorTarget), monitorTarget.timestamp)
  }
}
