package org.powerapi.core

import java.io.File

/**
 * This is not a monitoring target. It's an internal wrapper for the Thread IDentifier.
 *
 * @param tid: thread identifier
 */
case class Thread(tid: Long)

/**
 * Base trait use for implementing os specific methods.
 */
trait OSHelper {
  /**
   * Test whether a process exists.
   *
   * @param process: process to test.
   */
  def existsProcess(process: Process): Boolean

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

  def existsProcess(process: Process): Boolean = {
    val pidDirectory = new File(taskPath.replace("$pid", s"${process.pid}"))
    pidDirectory.exists && pidDirectory.isDirectory
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
