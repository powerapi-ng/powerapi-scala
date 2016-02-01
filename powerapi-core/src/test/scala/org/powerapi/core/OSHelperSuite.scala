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
package org.powerapi.core

import java.util.UUID
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.hyperic.sigar.SigarException
import org.powerapi.UnitTest
import org.powerapi.core.target.{All, Application, Container, Process, intToProcess, stringToApplication, TargetUsageRatio}
import org.powerapi.module.CacheKey
import scala.concurrent.duration.DurationInt

class OSHelperSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("OSHelperSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val basepath = getClass.getResource("/").getPath

  "The LinuxHelper" should "be able to read configuration parameters" in {
    val linuxHelper = new LinuxHelper

    linuxHelper.frequenciesPath should equal("p1/%?core")
    linuxHelper.taskPath should equal("p2/%?pid")
    linuxHelper.globalStatPath should equal("p3")
    linuxHelper.processStatPath should equal("p4/%?pid")
    linuxHelper.timeInStatePath should equal("p5")
    linuxHelper.topology should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
  }

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
      
      override def getProcesses(container: Container): Set[Process] = container match {
        case Container("ship") => Set(Process(4), Process(5))
        case Container("bad-ship") => Set(Process(-1), Process(4))
        case _ => Set()
      }

      def getProcessCpuTime(process: Process): Option[Long] = process match {
        case Process(1) => Some(33 + 2)
        case Process(2) => Some(10 + 5)
        case Process(3) => Some(3 + 5)
        case Process(4) => Some(6 + 8)
        case Process(5) => Some(20 + 7)
        case _ => None
      }

      def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0, 0)

      def getThreads(process: Process): Set[Thread] = Set()
      
      def getProcessCpuPercent(muid: UUID, process: Process): TargetUsageRatio = TargetUsageRatio(0.0)

      def getGlobalCpuPercent(muid: UUID): TargetUsageRatio = TargetUsageRatio(0.0)

      def getTimeInStates: TimeInStates = TimeInStates(Map())
    }

    val p1Time = 33 + 2
    val goodAppTime = 10 + 5 + 3 + 5
    val badAppTime = 10 + 5
    val goodShipTime = 6 + 8 + 20 + 7
    val badShipTime = 6 + 8

    helper.getTargetCpuTime(1) should equal(Some(p1Time))
    helper.getTargetCpuTime("app") should equal(Some(goodAppTime))
    helper.getTargetCpuTime("bad-app") should equal(Some(badAppTime))
    helper.getTargetCpuTime(Container("ship")) should equal(Some(goodShipTime))
    helper.getTargetCpuTime(Container("bad-ship")) should equal(Some(badShipTime))
    helper.getTargetCpuTime(All) should equal(None)
  }
  
  "The method getTargetCpuPercent in the OSHelper" should "return the cpu usage of the target" in {
    val helper = new OSHelper {
      def getCPUFrequencies: Set[Long] = Set()

      def getProcesses(application: Application): Set[Process] = application match {
        case Application("app") => Set(Process(2), Process(3))
        case Application("bad-app") => Set(Process(-1), Process(2))
        case _ => Set()
      }
      
      override def getProcesses(container: Container): Set[Process] = container match {
        case Container("ship") => Set(Process(4), Process(5))
        case Container("bad-ship") => Set(Process(-1), Process(4))
        case _ => Set()
      }

      def getProcessCpuTime(process: Process): Option[Long] = None

      def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0, 0)

      def getThreads(process: Process): Set[Thread] = Set()
      
      def getProcessCpuPercent(muid: UUID, process: Process): TargetUsageRatio = process match {
        case Process(1) => TargetUsageRatio(0.73)
        case Process(2) => TargetUsageRatio(0.49)
        case Process(3) => TargetUsageRatio(0.14)
        case Process(4) => TargetUsageRatio(0.67)
        case Process(5) => TargetUsageRatio(0.05)
        case _ => TargetUsageRatio(0.0)
      }

      def getGlobalCpuPercent(muid: UUID): TargetUsageRatio = TargetUsageRatio(0.0)

      def getTimeInStates: TimeInStates = TimeInStates(Map())
    }

    val p1Usage = 0.73
    val goodAppUsage = 0.49 + 0.14
    val badAppUsage = 0.49
    val goodShipUsage = 0.67 + 0.05
    val badShipUsage = 0.67

    helper.getTargetCpuPercent(UUID.randomUUID(), 1) should equal(TargetUsageRatio(p1Usage))
    helper.getTargetCpuPercent(UUID.randomUUID(), "app") should equal(TargetUsageRatio(goodAppUsage))
    helper.getTargetCpuPercent(UUID.randomUUID(), "bad-app") should equal(TargetUsageRatio(badAppUsage))
    helper.getTargetCpuPercent(UUID.randomUUID(), Container("ship")) should equal(TargetUsageRatio(goodShipUsage))
    helper.getTargetCpuPercent(UUID.randomUUID(), Container("bad-ship")) should equal(TargetUsageRatio(badShipUsage))
    helper.getTargetCpuPercent(UUID.randomUUID(), All) should equal(TargetUsageRatio(0.0))
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
  
  "The method getProcessCpuPercent in the LinuxHelper" should "return the process cpu usage in percentage of a given process" in {
    val muid = UUID.randomUUID()
    val helper = new LinuxHelper {
      override lazy val processStatPath = s"${basepath}proc/%?pid/stat"
      override lazy val globalStatPath  = s"${basepath}proc/stat"
      
      cpuTimesCache.update(new CacheKey(muid, 1), (30, 25954239))
    }

    helper.getProcessCpuPercent(muid, 1) should equal(TargetUsageRatio(0.05))
    helper.getProcessCpuPercent(muid, 10) should equal(TargetUsageRatio(0.0))
  }

  "The method getGlobalCpuPercent in the LinuxHelper" should "return the global cpu usage in percentage" in {
    val muid = UUID.randomUUID()
    val helper = new LinuxHelper {
      override lazy val globalStatPath = s"${basepath}proc/stat"
      
      cpuTimesCache.update(new CacheKey(muid, All), (70700, 25954239))
    }

    val badHelper = new LinuxHelper {
      override lazy val globalStatPath = s"${basepath}proc/stats"
    }

    helper.getGlobalCpuPercent(muid) should equal(TargetUsageRatio(0.45))
    badHelper.getGlobalCpuPercent(muid) should equal(TargetUsageRatio(0.0))
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

  "The SigarHelper" should "be able to read configuration parameters" in {
    val sigarHelper = new SigarHelper

    sigarHelper.libNativePath should equal("p2")
  }

  "The SigarHelper methods" should "return correct values" in {
    val helper = new SigarHelper {
      override lazy val libNativePath = "./powerapi-core/lib"
    }
    
    val pid = Process(java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split("@")(0).toInt)
    
    intercept[SigarException] { helper.getCPUFrequencies }
    helper.getProcesses(Application("java")).size should be > 0
    intercept[SigarException] { helper.getThreads(Process(1)) }
    helper.getProcessCpuTime(pid).get should be > 0L
    helper.getGlobalCpuTime match {
      case GlobalCpuTime(globalTime, activeTime) => {
        globalTime should be > 0L
        activeTime should be > 0L
      }
    }
    helper.getProcessCpuPercent(UUID.randomUUID(), pid).ratio should be > -1.0
    helper.getGlobalCpuPercent(UUID.randomUUID()).ratio should be > -1.0
    intercept[SigarException] { helper.getTimeInStates }
  }
}
