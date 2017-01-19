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

import scala.concurrent.Await

import akka.actor.Props
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout
import akka.pattern.gracefulStop

import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.core.target.{Process, Application, All}
import org.powerapi.core.{Disk, Tick, OSHelper, MessageBus}
import org.powerapi.module.SensorChannel.{stopSensor, startSensor}
import org.powerapi.module.Sensors
import org.powerapi.module.disk.UsageMetricsChannel.{TargetDiskUsageRatio, DiskUsageReport, subscribeDiskUsageReport}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration.DurationInt

class DiskSimpleSensorSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  trait Bus {
    val eventBus = new MessageBus
  }

  "A DiskSimpleSensor" should "handle MonitorTick messages and sense cpu metrics for the All target" in new Bus {
    val osHelper = mock[OSHelper]
    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    val tick3 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 2000
    }
    val muid = UUID.randomUUID()
    val target = All
    val disks = Seq(Disk("sda", 8, 0), Disk("sdb", 8, 16))
    osHelper.getGlobalDiskBytes _ expects disks returning Seq(Disk("sda", 8, 0, 10, 5), Disk("sdb", 8, 16, 1, 1))
    osHelper.getGlobalDiskBytes _ expects disks returning Seq(Disk("sda", 8, 0, 1000, 20), Disk("sdb", 8, 16, 1, 1))
    osHelper.getGlobalDiskBytes _ expects disks returning Seq(Disk("sda", 8, 0, 1000, 20), Disk("sdb", 8, 16, 11, 2000))

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[DiskSimpleSensor].getName}").intercept({
      startSensor(muid, target, classOf[DiskSimpleSensor], Seq(eventBus, muid, target, osHelper, disks))(eventBus)
    })
    subscribeDiskUsageReport(muid, target)(eventBus)(testActor)

    publishMonitorTick(muid, target, tick1)(eventBus)
    var usageReport = expectMsgClass(classOf[DiskUsageReport])
    usageReport.muid should equal(muid)
    usageReport.target should equal(target)
    usageReport.tick should equal(tick1)
    usageReport.usages should contain theSameElementsAs Seq(
      TargetDiskUsageRatio("sda", 1000 - 10, (1000 - 10) / (1000 - 10).toDouble, 20 - 5, (20 - 5) / (20 - 5).toDouble),
      TargetDiskUsageRatio("sdb", 0, 0d, 0, 0d)
    )

    publishMonitorTick(muid, target, tick2)(eventBus)
    usageReport = expectMsgClass(classOf[DiskUsageReport])
    usageReport.muid should equal(muid)
    usageReport.target should equal(target)
    usageReport.tick should equal(tick2)
    usageReport.usages should contain theSameElementsAs Seq(
      TargetDiskUsageRatio("sda", 0, 0d, 0, 0d),
      TargetDiskUsageRatio("sdb", 11 - 1, (11 - 1) / (11 - 1).toDouble, 2000 - 1, (2000 - 1) / (2000 - 1).toDouble)
    )

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[DiskSimpleSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, target, tick3)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }

  it should "handle MonitorTick messages and sense cpu metrics for the other targets" in new Bus {
    val osHelper = mock[OSHelper]
    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    val tick3 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 2000
    }
    val muid = UUID.randomUUID()
    val target = Application("firefox")
    val disks = Seq(Disk("sda", 8, 0), Disk("sdb", 8, 16))

    osHelper.getProcesses _ expects Application("firefox") returning Set(Process(1))
    osHelper.existsCGroup _ expects ("blkio", "powerapi/1") returning false
    osHelper.createCGroup _ expects ("blkio", "powerapi/1")
    osHelper.attachToCGroup _ expects ("blkio", "powerapi/1", "1")
    osHelper.getGlobalDiskBytes _ expects disks returning Seq(Disk("sda", 8, 0, 10, 5), Disk("sdb", 8, 16, 1, 1))
    osHelper.getTargetDiskBytes _ expects (disks, Application("firefox")) returning Seq(Disk("sda", 8, 0, 5, 5), Disk("sdb", 8, 16, 0, 0))
    osHelper.getProcesses _ expects Application("firefox") returning Set(Process(1), Process(2))
    osHelper.existsCGroup _ expects ("blkio", "powerapi/2") returning false
    osHelper.createCGroup _ expects ("blkio", "powerapi/2")
    osHelper.attachToCGroup _ expects ("blkio", "powerapi/2", "2")
    osHelper.getGlobalDiskBytes _ expects disks returning Seq(Disk("sda", 8, 0, 1000, 7), Disk("sdb", 8, 16, 1, 1))
    osHelper.getTargetDiskBytes _ expects (disks, Application("firefox")) returning Seq(Disk("sda", 8, 0, 750, 5), Disk("sdb", 8, 16, 0, 0))
    osHelper.getProcesses _ expects Application("firefox") returning Set(Process(1))
    osHelper.existsCGroup _ expects ("blkio", "powerapi/2") returning true
    osHelper.deleteCGroup _ expects ("blkio", "powerapi/2")
    osHelper.getGlobalDiskBytes _ expects disks returning Seq(Disk("sda", 8, 0, 1000, 7), Disk("sdb", 8, 16, 2000, 6000))
    osHelper.getTargetDiskBytes _ expects (disks, Application("firefox")) returning Seq(Disk("sda", 8, 0, 750, 5), Disk("sdb", 8, 16, 0, 3000))
    osHelper.getProcesses _ expects Application("firefox") returning Set(Process(1))
    osHelper.existsCGroup _ expects ("blkio", "powerapi/1") returning true
    osHelper.deleteCGroup _ expects ("blkio", "powerapi/1")

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[DiskSimpleSensor].getName}").intercept({
      startSensor(muid, target, classOf[DiskSimpleSensor], Seq(eventBus, muid, target, osHelper, disks))(eventBus)
    })
    subscribeDiskUsageReport(muid, target)(eventBus)(testActor)

    publishMonitorTick(muid, target, tick1)(eventBus)
    var usageReport = expectMsgClass(classOf[DiskUsageReport])
    usageReport.muid should equal(muid)
    usageReport.target should equal(target)
    usageReport.tick should equal(tick1)
    usageReport.usages should contain theSameElementsAs Seq(
      TargetDiskUsageRatio("sda", 1000 - 10, (750 - 5) / (1000 - 10).toDouble, 7 - 5, (5 - 5) / (7 - 5).toDouble),
      TargetDiskUsageRatio("sdb", 0, 0d, 0, 0d)
    )

    publishMonitorTick(muid, target, tick1)(eventBus)
    usageReport = expectMsgClass(classOf[DiskUsageReport])
    usageReport.muid should equal(muid)
    usageReport.target should equal(target)
    usageReport.tick should equal(tick1)
    usageReport.usages should contain theSameElementsAs Seq(
      TargetDiskUsageRatio("sda", 0, 0d, 0, 0d),
      TargetDiskUsageRatio("sdb", 2000 - 1, (0) / (2000 - 1).toDouble, 6000 - 1, (3000 - 0) / (6000 - 1).toDouble)
    )

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[DiskSimpleSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, target, tick3)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }
}
