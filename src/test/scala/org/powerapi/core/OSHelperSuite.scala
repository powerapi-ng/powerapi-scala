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
package org.powerapi.core

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import scala.concurrent.duration.DurationInt

class OSHelperSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SimpleCpuSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val basepath = getClass.getResource("/").getPath

  "The method getThreads in the LinuxHelper" should "return the threads created by a given process" in {
    val helper = new LinuxHelper {
      override lazy val taskPath =  s"${basepath}proc/%?pid/task"
    }

    helper.getThreads(Process(1)) should equal(List(Thread(1000), Thread(1001)))
  }

  "The method getTargetCpuUsageRatio in the OSHelper" should "return the cpu usage of the target" in {
    val helper = new OSHelper {
      override def getProcesses(application: Application): List[Process] = application match {
        case Application("app") => List(Process(2), Process(3))
        case Application("bad-app") => List(Process(-1), Process(2))
        case _ => List()
      }

      override def getProcessCpuTime(process: org.powerapi.core.Process): Option[Long] = process match {
        case Process(1) => Some(33 + 2)
        case Process(2) => Some(10 + 5)
        case Process(3) => Some(3 + 5)
        case _ => None
      }

      override def getGlobalCpuTime(): Option[Long] = Some(43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0)

      override def getThreads(process: Process): List[Thread] = List()

      override def getTimeInStates(): TimeInStates = TimeInStates(Map())
    }

    val globalTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val p1Ratio = (33 + 2).doubleValue / globalTime
    val goodAppRatio = (10 + 5 + 3 + 5).doubleValue / globalTime
    val badAppRatio = (10 + 5).doubleValue / globalTime
    val allRatio = 0d

    helper.getTargetCpuUsageRatio(Process(1)) match {
      case TargetUsageRatio(ratio) if p1Ratio == ratio => assert(true)
      case _ => assert(false)
    }

    helper.getTargetCpuUsageRatio(Application("app")) match {
      case TargetUsageRatio(ratio) if goodAppRatio == ratio => assert(true)
      case _ => assert(false)
    }

    helper.getTargetCpuUsageRatio(Application("bad-app")) match {
      case TargetUsageRatio(ratio) if badAppRatio == ratio => assert(true)
      case _ => assert(false)
    }

    helper.getTargetCpuUsageRatio(All) match {
      case TargetUsageRatio(ratio) if allRatio == ratio => assert(true)
      case _ => assert(false)
    }
  }

  "The method getProcessCpuTime in the LinuxHelper" should "return the process cpu time of a given process" in {
    val helper = new LinuxHelper {
      override lazy val processStatPath = s"${basepath}proc/%?pid/stat"
    }

    helper.getProcessCpuTime(Process(1)) match {
      case Some(35) => assert(true)
      case _ => assert(false)
    }

    helper.getProcessCpuTime(Process(10)) match {
      case None => assert(true)
      case _ => assert(false)
    }
  }

  "The method getGlobalCpuTime in the LinuxHelper" should "return the global cpu time" in {
    val helper = new LinuxHelper {
      override lazy val globalStatPath = s"${basepath}/proc/stat"
    }

    val badHelper = new LinuxHelper {
      override lazy val globalStatPath = s"${basepath}/proc/stats"
    }

    val globalTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0

    helper.getGlobalCpuTime() match {
      case Some(globalTime) => assert(true)
      case _ => assert(false)
    }

    badHelper.getGlobalCpuTime() match {
      case None => assert(true)
      case _ => assert(false)
    }
  }

  "The method getTimeInStates in the LinuxHelper" should "return the time spent by the CPU in each frequency if the dvfs is enabled" in {
    val helper = new LinuxHelper {
      override lazy val timeInStatePath = s"${basepath}sys/devices/system/cpu/cpu%?index/cpufreq/stats/time_in_state"
      override lazy val cores = 4
    }

    val badHelper = new LinuxHelper {
      override lazy val timeInStatePath = s"${basepath}/sys/devices/system/cpu/cpu%?index/cpufreq/stats/time_in_states"
      override lazy val cores = 4
    }

    helper.getTimeInStates() should equal(
      TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l))
    )

    badHelper.getTimeInStates() should equal(TimeInStates(Map()))
  }

  "A TimeInStates case class" should "compute the difference with another one" in {
    val timesLeft = TimeInStates(Map(1l -> 10l, 2l -> 20l, 3l -> 30l, 4l -> 15l))
    val timesRight = TimeInStates(Map(1l -> 1l, 2l -> 2l, 3l -> 3l, 100l -> 100l))

    (timesLeft - timesRight) should equal(TimeInStates(Map(1l -> 9l, 2l -> 18l, 3l -> 27l, 4l -> 15l)))
  }
}
