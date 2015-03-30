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
package org.powerapi.core

import com.typesafe.config.Config
import java.io.{IOException, File}
import org.apache.logging.log4j.LogManager
import org.hyperic.sigar.{Sigar, SigarException, SigarProxyCache}
import org.hyperic.sigar.ptql.ProcessFinder
import org.powerapi.core.FileHelper.using
import org.powerapi.core.target.{Application, Process, Target}
import scala.collection.JavaConversions._
import scala.sys.process._

/**
 * This is not a monitoring target. It's an internal wrapper for the Thread IDentifier.
 *
 * @param tid: thread identifier
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
case class Thread(tid: Int)

/**
 * Wrapper class for the time spent by the cpu in each frequency (if dvfs enabled).
 *
 * @author <a href="mailto:aurelien.bourdon@gmail.com">Aurélien Bourdon</a
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
case class TimeInStates(times: Map[Long, Long]) {
  def -(that: TimeInStates) =
    TimeInStates((for ((frequency, time) <- times) yield (frequency, time - that.times.getOrElse(frequency, 0: Long))).toMap)
}

/**
 * Wrapper class for the global cpu times. It includes the global time and the time consumed by the CPU.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
case class GlobalCpuTime(globalTime: Long, activeTime: Long)

/**
 * Base trait use for implementing os specific methods.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
trait OSHelper {
  /**
   * Get the list of frequencies available on the CPU.
   */
  def getCPUFrequencies: Set[Long]

  /**
   * Get the list of processes behind an Application.
   *
   * @param application: targeted application.
   */
  def getProcesses(application: Application): Set[Process]

  /**
   * Get the list of thread behind a Process.
   *
   * @param process: targeted process.
   */
  def getThreads(process: Process): Set[Thread]

  /**
   * Get the process execution time on the cpu.
   *
   * @param process: targeted process
   */
  def getProcessCpuTime(process: Process): Option[Long]

  /**
   * Get the global execution time for the cpu.
   */
  def getGlobalCpuTime: GlobalCpuTime

  /**
   * Get how many time CPU spent under each frequency.
   */
  def getTimeInStates: TimeInStates

  /**
   * Get the target cpu time.
   */
  def getTargetCpuTime(target: Target): Option[Long] = {
    target match {
      case process: Process => getProcessCpuTime(process)
      case application: Application => Some(
        getProcesses(application).foldLeft(0: Long) {
          (acc, process: Process) => {
            getProcessCpuTime(process) match {
              case Some(value) => acc + value
              case _ => acc
            }
          }
        }
      )
      case _ => None
    }
  }
}

/**
 * Linux special helper.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LinuxHelper extends OSHelper with Configuration {
  private val log = LogManager.getLogger

  private val PSFormat = """^\s*(\d+)\s*""".r
  private val GlobalStatFormat = """cpu\s+([\d\s]+)""".r
  private val TimeInStateFormat = """(\d+)\s+(\d+)""".r

  /**
   * This file allows to get all the cpu frequencies with the help of procfs and cpufreq_utils.
   */
  lazy val frequenciesPath = load { _.getString("powerapi.procfs.frequencies-path") } match {
    case ConfigValue(path) if path.contains("%?core") => path
    case _ => "/sys/devices/system/cpu/cpu%?core/cpufreq/scaling_available_frequencies"
  }

  /**
   * This file allows to get all threads associated to one PID with the help of the procfs.
   */
  lazy val taskPath = load { _.getString("powerapi.procfs.process-task-path") } match {
    case ConfigValue(path) if path.contains("%?pid") => path
    case _ => "/proc/%?pid/task"
  }

  /**
   * Global stat file, giving global information of the system itself.
   * Typically presents under /proc/stat.
   */
  lazy val globalStatPath = load { _.getString("powerapi.procfs.global-path") } match {
    case ConfigValue(path) => path
    case _ => "/proc/stat"
  }

  /**
   * Process stat file, giving information about the process itself.
   * Typically presents under /proc/[pid]/stat.
   */
  lazy val processStatPath = load { _.getString("powerapi.procfs.process-path") } match {
    case ConfigValue(path) if path.contains("%?pid") => path
    case _ => "/proc/%?pid/stat"
  }

  /**
   * Time in state file, giving information about how many time CPU spent under each frequency.
   */
  lazy val timeInStatePath = load { _.getString("powerapi.sysfs.timeinstates-path") } match {
    case ConfigValue(path) => path
    case _ => "/sys/devices/system/cpu/cpu%?index/cpufreq/stats/time_in_state"
  }

  /**
   * CPU's topology.
   */
  lazy val topology: Map[Int, Set[Int]] = load { conf =>
    (for (item: Config <- conf.getConfigList("powerapi.cpu.topology"))
      yield (item.getInt("core"), item.getDoubleList("indexes").map(_.toInt).toSet)).toMap
  } match {
    case ConfigValue(values) => values
    case _ => Map()
  }

  def getCPUFrequencies: Set[Long] = {
    (for(index <- topology.values.flatten) yield {
      try {
        using(frequenciesPath.replace("%?core", s"$index"))(source => {
          log.debug("using {} as a frequencies path", frequenciesPath)
          source.getLines.toIndexedSeq(0).split("\\s").map(_.toLong).toSet
        })
      }
      catch {
        case ioe: IOException => log.warn("i/o exception: {}", ioe.getMessage); Set[Long]()
      }
    }).flatten.toSet
  }

  def getProcesses(application: Application): Set[Process] = {
    Seq("ps", "-C", application.name, "-o", "pid", "--no-headers").lineStream_!.map {
      case PSFormat(pid) => Process(pid.toInt)
    }.toSet
  }

  def getThreads(process: Process): Set[Thread] = {
    val pidDirectory = new File(taskPath.replace("%?pid", s"${process.pid}"))

    if (pidDirectory.exists && pidDirectory.isDirectory) {
      /**
       * The pid is removed because it corresponds to the main thread.
       */
      pidDirectory.listFiles.filter(dir => dir.isDirectory && dir.getName != s"${process.pid}").map(dir => Thread(dir.getName.toInt)).toSet
    }
    else Set[Thread]()
  }

  def getProcessCpuTime(process: Process): Option[Long] = {
    try {
      using(processStatPath.replace("%?pid", s"${process.pid}"))(source => {
        log.debug("using {} as a procfs process stat path", processStatPath)

        val statLine = source.getLines.toIndexedSeq(0).split("\\s")
        // User time + System time
        Some(statLine(13).toLong + statLine(14).toLong)
      })
    }
    catch {
      case ioe: IOException => log.warn("i/o exception: {}", ioe.getMessage); None
    }
  }

  def getGlobalCpuTime: GlobalCpuTime = {
    try {
      using(globalStatPath)(source => {
        log.debug("using {} as a procfs global stat path", globalStatPath)

        val cpuTimes = source.getLines.toIndexedSeq(0) match {
          /**
           * Exclude all guest columns, they are already added in utime column.
           *
           * @see http://lxr.free-electrons.com/source/kernel/sched/cputime.c#L165
           */
          case GlobalStatFormat(times) => {
            val globalTime = times.split("\\s").slice(0, 8).foldLeft(0: Long) {
              (acc, x) => acc + x.toLong
            }
            val activeTime = globalTime - times.split("\\s")(3).toLong

            GlobalCpuTime(globalTime, activeTime)
          }
          case _ => log.warn("unable to parse line from {}", globalStatPath); GlobalCpuTime(0, 0)
        }

        cpuTimes
      })
    }
    catch {
      case ioe: IOException => log.warn("i/o exception: {}", ioe.getMessage); GlobalCpuTime(0, 0)
    }
  }

  def getTimeInStates: TimeInStates = {
    val result = collection.mutable.HashMap[Long, Long]()

    for(core <- topology.keys) {
      try {
        using(timeInStatePath.replace("%?index", s"$core"))(source => {
          log.debug("using {} as a sysfs timeinstates path", timeInStatePath)

          for(line <- source.getLines) {
            line match {
              case TimeInStateFormat(freq, t) => result += (freq.toLong -> (t.toLong + result.getOrElse(freq.toLong, 0l)))
              case _ => log.warn("unable to parse line {} from file {}", line, timeInStatePath)
            }
          }
        })
      }
      catch {
        case ioe: IOException => log.warn("i/o exception: {}", ioe.getMessage);
      }
    }

    TimeInStates(result.toMap[Long, Long])
  }
}

/**
 * SIGAR special helper.
 *
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
class SigarHelper extends OSHelper {
  private val log = LogManager.getLogger

  /**
   * SIGAR's proxy instance.
   */
  System.setProperty("java.library.path", "./powerapi-core/lib")
  lazy val sigar = SigarProxyCache.newInstance(new Sigar(), 100)

  def getCPUFrequencies: Set[Long] = throw new SigarException("sigar cannot be able to get CPU frequencies")

  def getProcesses(application: Application): Set[Process] = Set(ProcessFinder.find(sigar, "State.Name.eq="+application.name).map(l => Process(l.toInt)):_*)

  def getThreads(process: Process): Set[Thread] = throw new SigarException("sigar cannot be able to get process threads")

  def getProcessCpuTime(process: Process): Option[Long] = {
    try {
      Some(sigar.getProcTime(process.pid.toLong).getTotal)
    }
    catch {
      case se: SigarException => log.warn("sigar exception: {}", se.getMessage); None
    }
  }

  def getGlobalCpuTime: GlobalCpuTime = {
    try {
      val globalTime = sigar.getCpu.getTotal
      val activeTime = globalTime - sigar.getCpu.getIdle

      GlobalCpuTime(globalTime, activeTime)
    }
    catch {
      case se: SigarException => log.warn("sigar exception: {}", se.getMessage); GlobalCpuTime(0, 0)
    }
  }

  def getTimeInStates: TimeInStates = throw new SigarException("sigar cannot be able to get how many time CPU spent under each frequency")
}
