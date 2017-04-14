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

import java.io.File

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.hyperic.sigar.{Sigar, SigarException, SigarProxyCache}
import org.powerapi.UnitTest
import org.powerapi.core.target.{All, Application, Container, Process, Target}

class OSHelperSuite extends UnitTest {

  val timeout = Timeout(1.seconds)
  val basepath = getClass.getResource("/").getPath

  override def afterAll() = {
    system.terminate()
  }

  "An OSHelper" should "allow to get the target processes and to get the target cpu time" in {
    val helper = new OSHelper {
      def createCGroup(subsystem: String, name: String): Unit = ???

      def attachToCGroup(subsystem: String, name: String, toAttach: String): Unit = ???

      def getDiskInfo(names: Seq[String]): Seq[Disk] = ???

      def deleteCGroup(subsystem: String, name: String): Unit = ???

      def existsCGroup(subsystem: String, name: String): Boolean = ???

      def getTimeInStates: TimeInStates = ???

      def getThreads(process: Process): Set[Thread] = ???

      def getCPUFrequencies: Set[Long] = ???

      def getProcessCpuTime(process: Process): Long = process match {
        case Process(1) => 10
        case Process(2) => 11
        case Process(10) => 30
        case _ => -10
      }

      def getDockerContainerCpuTime(container: Container): Long = container match {
        case Container("abcd", "n") => 20 + 21
        case _ => -10
      }

      def getGlobalCpuTimes: GlobalCpuTimes = ???

      def getProcesses(target: Target): Set[Process] = target match {
        case Application("firefox") => Set(1, 2)
        case Process(10) => Set(10)
      }

      def getGlobalDiskBytes(disks: Seq[Disk]): Seq[Disk] = ???

      def getTargetDiskBytes(disks: Seq[Disk], target: Target): Seq[Disk] = ???

      def cgroupMntPoint(name: String): Option[String] = ???
    }

    helper.getProcesses(Application("firefox")) should equal(Set(Process(1), Process(2)))
    helper.getProcesses(Process(10)) should equal(Set(Process(10)))
    helper.getTargetCpuTime(Process(10)) should equal(30)
    helper.getTargetCpuTime(Application("firefox")) should equal(10 + 11)
    helper.getTargetCpuTime(Container("abcd", "n")) should equal(20 + 21)
    helper.getTargetCpuTime(All) should equal(0)
    helper.getAllDirectories(new File(s"${basepath}/sys/fs/cgroup/blkio")) should contain theSameElementsAs Seq(
      new File(s"${basepath}/sys/fs/cgroup/blkio/powerapi"),
      new File(s"${basepath}/sys/fs/cgroup/blkio/powerapi/1"),
      new File(s"${basepath}/sys/fs/cgroup/blkio/powerapi/2"),
      new File(s"${basepath}/sys/fs/cgroup/blkio/test")
    )
  }

  "The LinuxHelper" should "be able to read configuration parameters" in {
    val linuxHelper = new LinuxHelper

    linuxHelper.frequenciesPath should equal("p1/%?core")
    linuxHelper.taskPath should equal("p2/%?pid")
    linuxHelper.globalStatPath should equal("p3")
    linuxHelper.processStatPath should equal("p4/%?pid")
    linuxHelper.timeInStatePath should equal("p5")
    linuxHelper.cgroupSysFSPath should equal("p6")
    linuxHelper.diskStatPath should equal("p7")
    linuxHelper.topology should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    linuxHelper.mountsPath should equal("p1/mounts")
  }

  it should "return the list of available frequencies" in {
    val helper = new LinuxHelper {
      override lazy val topology = Map(0 -> Set(0), 1 -> Set(1), 2 -> Set(2), 3 -> Set(3))
      override lazy val frequenciesPath = s"${basepath}sys/devices/system/cpu/cpu%?core/cpufreq/scaling_available_frequencies"
    }

    helper.getCPUFrequencies should contain allOf(1596000l, 1729000l, 1862000l, 1995000l, 2128000l, 2261000l, 2394000l, 2527000l, 2660000l)
  }

  it should "return the threads created by a given process" in {
    val helper = new LinuxHelper {
      override lazy val taskPath = s"${basepath}proc/%?pid/task"
    }

    helper.getThreads(1) should contain allOf(Thread(1000), Thread(1001))
  }

  it should "return the process cpu time of a given process" in {
    val helper = new LinuxHelper {
      override lazy val processStatPath = s"${basepath}proc/%?pid/stat"
    }

    helper.getProcessCpuTime(1) should equal(35)
    helper.getProcessCpuTime(10) should equal(0)
  }

  it should "return the cgroup mount point if it exists" in {
    val helper = new LinuxHelper {
      override lazy val mountsPath = s"${basepath}proc/mounts"
    }

    helper.cgroupMntPoint("cpuacct") should equal (Some("/sys/fs/cgroup/cpuacct"))
    helper.cgroupMntPoint("test") should equal (None)
  }

  it should "return the cpu time of a given docker container" in {
    val helper = new LinuxHelper {
      override def cgroupMntPoint(name: String): Option[String] = Some(s"${basepath}sys/fs/cgroup/cpuacct")
    }

    helper.getDockerContainerCpuTime(Container("abcd", "n")) should equal(2502902 + 277405)
    helper.getDockerContainerCpuTime(Container("test", "n2")) should equal(0)
  }

  it should "return the global cpu time" in {
    val helper = new LinuxHelper {
      override lazy val globalStatPath = s"${basepath}proc/stat"
    }

    val badHelper = new LinuxHelper {
      override lazy val globalStatPath = s"${basepath}proc/stats"
    }

    val idleTime = 25883594
    val globalTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val activeTime = globalTime - idleTime

    helper.getGlobalCpuTimes should equal(GlobalCpuTimes(idleTime, activeTime))
    badHelper.getGlobalCpuTimes should equal(GlobalCpuTimes(0, 0))
  }

  it should "return the time spent by the CPU in each frequency if the dvfs is enabled" in {
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

  it should "get information about selected disks" in {
    val helper = new LinuxHelper {
      override lazy val diskStatPath = s"${basepath}/proc/diskstats"
    }

    helper.getDiskInfo(Seq("sda", "sdb", "test")) should contain theSameElementsAs Seq(Disk("sda", 8, 0), Disk("sdb", 8, 16))
  }

  it should "return the global disk bytes information" in {
    val helper = new LinuxHelper {
      override lazy val cgroupSysFSPath = s"${basepath}/sys/fs/cgroup"
    }

    val bytesRead = Map(
      "sda" -> (1743688304128l + 2148007936l + 2148007936l + 2148007936l),
      "sdb" -> (3969024l + 2147483648l)
    )

    val bytesWritten = Map(
      "sda" -> (4773668771328l + 128828350464l + 128828350464l + 128828350464l),
      "sdb" -> (5242880l + 51546972160l)
    )

    helper.getGlobalDiskBytes(Seq(Disk("sda", 8, 0), Disk("sdb", 8, 16))) should contain theSameElementsAs Seq(
      Disk("sda", 8, 0, bytesRead("sda"), bytesWritten("sda")),
      Disk("sdb", 8, 16, bytesRead("sdb"), bytesWritten("sdb"))
    )
  }

  it should "return the disk bytes information for a given target" in {
    val helper = new LinuxHelper {
      override lazy val cgroupSysFSPath = s"${basepath}/sys/fs/cgroup"

      override def getProcesses(target: Target): Set[Process] = target match {
        case Application("firefox") => Set(1, 2)
        case _ => Set()
      }
    }

    val bytesRead = Map(
      "sda" -> (2148007936l + 2148007936l)
    )

    val bytesWritten = Map(
      "sda" -> (128828350464l + 128828350464l)
    )

    helper.getTargetDiskBytes(Seq(Disk("sda", 8, 0)), Application("firefox")) should contain theSameElementsAs Seq(
      Disk("sda", 8, 0, bytesRead("sda"), bytesWritten("sda"))
    )
  }

  "A Disk case class" should "allow to compute the difference between two instances" in {
    val disk1T1 = Disk("sda", 0, 8, 10, 20)
    val disk1T2 = Disk("sda", 0, 8, 12, 22)
    val disk1T3 = Disk("sda", 0, 8, 12, 22)
    val disk2T1 = Disk("sdb", 0, 16, 22, 44)

    disk1T2 - disk1T1 should equal(Disk("sda", 0, 8, 2, 2))
    disk1T3 - disk1T2 should equal(Disk("sda", 0, 8, 0, 0))
    disk2T1 - disk1T3 should equal(disk2T1)
  }

  "A TimeInStates case class" should "allow to compute the difference between two instances" in {
    val timesLeft = TimeInStates(Map(1l -> 10l, 2l -> 20l, 3l -> 30l, 4l -> 15l))
    val timesRight = TimeInStates(Map(1l -> 1l, 2l -> 2l, 3l -> 3l, 100l -> 100l))

    (timesLeft - timesRight) should equal(TimeInStates(Map(1l -> 9l, 2l -> 18l, 3l -> 27l, 4l -> 15l)))
  }

  "The SigarHelperConfiguration" should "be able to read configuration parameters" in {
    val sigarHelper = new SigarHelperConfiguration {}

    sigarHelper.libNativePath should equal("./../external-libs/sigar-bin")
  }

  "The SigarHelper" should "work on most systems" in {
    val sigar = {
      System.setProperty("java.library.path", s"${basepath}sigar-bin")
      SigarProxyCache.newInstance(new Sigar(), 100)
    }

    val helper = new SigarHelper(sigar)
    val pid = Process(java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split("@")(0).toInt)

    intercept[SigarException] {
      helper.getCPUFrequencies
    }
    helper.getProcesses(Application("java")).size should be > 0
    intercept[SigarException] {
      helper.getThreads(Process(1))
    }
    helper.getProcessCpuTime(pid) should be > 0L
    helper.getGlobalCpuTimes match {
      case GlobalCpuTimes(idleTime, activeTime) => {
        idleTime should be > 0L
        activeTime should be > 0L
      }
    }
    intercept[SigarException] {
      helper.getTimeInStates
    }
  }
}
