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
import org.powerapi.core.target.{All, Application, Process, intToProcess, stringToApplication}
import scala.concurrent.duration.DurationInt

class OSHelperSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("OSHelperSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val basepath = getClass.getResource("/").getPath

  "The method getCPUFrequencies in the LinuxHelper" should "return the list of available frequencies" in {
    val helper = new LinuxHelper {
      override lazy val frequenciesPath = s"${basepath}sys/devices/system/cpu/cpu%?core/cpufreq/scaling_available_frequencies"
    }

    helper.getCPUFrequencies should contain allOf(1596000l, 1729000l, 1862000l, 1995000l, 2128000l, 2261000l, 2394000l, 2527000l, 2660000l)
  }

  "The method getThreads in the LinuxHelper" should "return the threads created by a given process" in {
    val helper = new LinuxHelper {
      override lazy val taskPath =  s"${basepath}proc/%?pid/task"
    }

    helper.getThreads(1) should contain allOf(Thread(1000), Thread(1001))
  }

  "The method getTargetCpuTime in the OSHelper" should "return the cpu usage of the target" in {
    val helper = new OSHelper {
      def getCPUFrequencies: Set[Long] = Set()

      def getProcesses(application: Application): Set[Process] = application match {
        case Application("app") => Set(Process(2), Process(3))
        case Application("bad-app") => Set(Process(-1), Process(2))
        case _ => Set()
      }

      def getProcessCpuTime(process: Process): Option[Long] = process match {
        case Process(1) => Some(33 + 2)
        case Process(2) => Some(10 + 5)
        case Process(3) => Some(3 + 5)
        case _ => None
      }

      def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0, 0)

      def getThreads(process: Process): Set[Thread] = Set()

      def getTimeInStates: TimeInStates = TimeInStates(Map())
      
      def getRAPLEnergy: Double = 0.0
    }

    val p1Time = 33 + 2
    val goodAppTime = 10 + 5 + 3 + 5
    val badAppTime = 10 + 5

    helper.getTargetCpuTime(1) should equal(Some(p1Time))
    helper.getTargetCpuTime("app") should equal(Some(goodAppTime))
    helper.getTargetCpuTime("bad-app") should equal(Some(badAppTime))
    helper.getTargetCpuTime(All) should equal(None)
  }

  "The method getProcessCpuTime in the LinuxHelper" should "return the process cpu time of a given process" in {
    val helper = new LinuxHelper {
      override lazy val processStatPath = s"${basepath}proc/%?pid/stat"
    }

    helper.getProcessCpuTime(1) should equal(Some(35))
    helper.getProcessCpuTime(10) should equal(None)
  }

  "The method getGlobalCpuTime in the LinuxHelper" should "return the global cpu time" in {
    val helper = new LinuxHelper {
      override lazy val globalStatPath = s"${basepath}proc/stat"
    }

    val badHelper = new LinuxHelper {
      override lazy val globalStatPath = s"${basepath}proc/stats"
    }

    val globalTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val activeTime = globalTime - 25883594

    helper.getGlobalCpuTime should equal(GlobalCpuTime(globalTime, activeTime))
    badHelper.getGlobalCpuTime should equal(GlobalCpuTime(0, 0))
  }

  "The method getTimeInStates in the LinuxHelper" should "return the time spent by the CPU in each frequency if the dvfs is enabled" in {
    val helper = new LinuxHelper {
      override lazy val timeInStatePath = s"${basepath}sys/devices/system/cpu/cpu%?index/cpufreq/stats/time_in_state"
      override lazy val topology = Map(0 -> Set(0), 1 -> Set(1), 2 -> Set(2), 3 -> Set(3))
    }

    val badHelper = new LinuxHelper {
      override lazy val timeInStatePath = s"${basepath}sys/devices/system/cpu/cpu%?index/cpufreq/stats/time_in_states"
      override lazy val topology = Map(0 -> Set(0), 1 -> Set(1), 2 -> Set(2), 3 -> Set(3))
    }

    helper.getTimeInStates should equal(
      TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l))
    )

    badHelper.getTimeInStates should equal(TimeInStates(Map()))
  }

  "A TimeInStates case class" should "compute the difference with another one" in {
    val timesLeft = TimeInStates(Map(1l -> 10l, 2l -> 20l, 3l -> 30l, 4l -> 15l))
    val timesRight = TimeInStates(Map(1l -> 1l, 2l -> 2l, 3l -> 3l, 100l -> 100l))

    (timesLeft - timesRight) should equal(TimeInStates(Map(1l -> 9l, 2l -> 18l, 3l -> 27l, 4l -> 15l)))
  }
}
