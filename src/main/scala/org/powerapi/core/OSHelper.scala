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

/**
 * This is not a monitoring target. It's an internal wrapper for the Thread IDentifier.
 *
 * @param tid: thread identifier
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
case class Thread(tid: Long)

/**
 * Wrapper class for the time spent by the cpu in each frequency (if dvfs enabled).
 *
 * @author Aurélien Bourdon <aurelien.bourdon@gmail.com>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
case class TimeInStates(times: Map[Long, Long]) {
  def -(that: TimeInStates) =
    TimeInStates((for ((frequency, time) <- times) yield (frequency, time - that.times.getOrElse(frequency, 0: Long))).toMap)
}

/**
 * Base trait use for implementing os specific methods.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
trait OSHelper {
  /**
   * Get the list of processes behind an Application.
   *
   * @param application: targeted application.
   */
  def getProcesses(application: Application): List[Process]

  /**
   * Get the list of thread behind a Process.
   *
   * @param process: targeted process.
   */
  def getThreads(process: Process): List[Thread]

  /**
   * Get the process execution time on the cpu.
   *
   * @param process: targeted process
   */
  def getProcessCpuTime(process: Process): Option[Long]

  /**
   * Get the global execution time for the cpu.
   */
  def getGlobalCpuTime(): Option[Long]

  /**
   * Get how many time CPU spent under each frequency.
   */
  def getTimeInStates(): TimeInStates
}

/**
 * Number of logical cores / Configuration.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
trait LogicalCoresConfiguration {
  self: Configuration =>

  lazy val cores = load { _.getInt("powerapi.hardware.cores") } match {
    case ConfigValue(nbCores) => nbCores
    case _ => 0
  }
}

/**
 * Linux special helper.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class LinuxHelper extends OSHelper with Configuration with LogicalCoresConfiguration {

  import java.io.{IOException, File}
  import org.apache.logging.log4j.LogManager
  import org.powerapi.core.FileHelper.using
  import scala.io.Source
  import scala.sys.process.stringSeqToProcess

  private val log = LogManager.getLogger

  private val PSFormat = """^\s*(\d+)""".r
  private val GlobalStatFormat = """cpu\s+([\d\s]+)""".r
  private val TimeInStateFormat = """(\d+)\s+(\d+)""".r

  /**
   * This file allows to get all threads associated to one PID with the help of the procfs.
   */
  lazy val taskPath = load { _.getString("powerapi.procfs.process-task-task") } match {
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

  def getProcesses(application: Application): List[Process] = {
    Seq("ps", "-C", application.name, "-o", "pid", "--no-headers").!!.split("\n").toList.map {
      case PSFormat(pid) => Process(pid.toLong)
    }
  }

  def getThreads(process: Process): List[Thread] = {
    val pidDirectory = new File(taskPath.replace("%?pid", s"${process.pid}"))

    if (pidDirectory.exists && pidDirectory.isDirectory) {
      /**
       * The pid is removed because it corresponds to the main thread.
       */
      pidDirectory.listFiles.filter(dir => dir.isDirectory && dir.getName != s"${process.pid}").toList.map(dir => Thread(dir.getName.toLong))
    }
    else List()
  }

  def getProcessCpuTime(process: Process): Option[Long] = {
    try {
      using(Source.fromFile(processStatPath.replace("%?pid", s"${process.pid}")))(source => {
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

  def getGlobalCpuTime(): Option[Long] = {
    try {
      using(Source.fromFile(globalStatPath))(source => {
        log.debug("using {} as a procfs global stat path", globalStatPath)

        val time = source.getLines.toIndexedSeq(0) match {
          /**
           * Exclude all guest columns, they are already added in utime column.
           *
           * @see http://lxr.free-electrons.com/source/kernel/sched/cputime.c#L165
           */
          case GlobalStatFormat(times) => Some(
            times.split("\\s").slice(0, 8).foldLeft(0: Long) {
              (acc, x) => acc + x.toLong
            }
          )
          case _ => log.warn("unable to parse line from {}", globalStatPath); None
        }

        time
      })
    }
    catch {
      case ioe: IOException => log.warn("i/o exception: {}", ioe.getMessage); None
    }
  }

  def getTimeInStates(): TimeInStates = {
    val result = collection.mutable.HashMap[Long, Long]()

    for(core <- 0 until cores) {
      try {
        using(Source.fromFile(timeInStatePath.replace("%?index", s"$core")))(source => {
          log.debug("using {} as a sysfs timeinstates path", timeInStatePath)

          for(line <- source.getLines) {
            line match {
              case TimeInStateFormat(freq, t) => result += (freq.toLong -> (t.toLong + (result.getOrElse(freq.toLong, 0l))))
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
