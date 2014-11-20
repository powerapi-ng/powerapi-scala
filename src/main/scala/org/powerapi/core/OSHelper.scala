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

import java.io.File

/**
 * This is not a monitoring target. It's an internal wrapper for the Thread IDentifier.
 *
 * @author mcolmant
 * @param tid: thread identifier
 */
case class Thread(tid: Long)

/**
 * Base trait use for implementing os specific methods.
 *
 * @author mcolmant
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
}

/**
 * Linux special helper.
 *
 * @author mcolmant
 */
object LinuxHelper extends OSHelper with Configuration {
  import scala.sys.process.stringSeqToProcess

  val PSFormat = """^\s*(\d+)""".r
  /**
   * This file allows to get all threads associated to one PID with the help of the procfs.
   */
  lazy val taskPath = load { _.getString("powerapi.procfs.process-task") } match {
    case ConfigValue(path) if path.contains("$pid") => path
    case _ => "/proc/$pid/task"
  }

  def getProcesses(application: Application): List[Process] = {
    Seq("ps", "-C", application.name, "-o", "pid", "--no-headers").!!.split("\n").toList.map {
      case PSFormat(pid) => Process(pid.toLong)
    }
  }

  def getThreads(process: Process): List[Thread] = {
    val pidDirectory = new File(taskPath.replace("$pid", s"${process.pid}"))

    if(pidDirectory.exists && pidDirectory.isDirectory) {
      /**
       * The pid is removed because it corresponds to the main thread.
       */
      pidDirectory.listFiles.filter(dir => dir.isDirectory && dir.getName != s"${process.pid}").toList.map(dir => Thread(dir.getName.toLong))
    }

    else List()
  }
}
