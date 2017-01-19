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
package org.powerapi.module.cpu.dvfs

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.core.target.{All, Application, TargetUsageRatio}
import org.powerapi.core.{GlobalCpuTimes, MessageBus, OSHelper, Tick, TimeInStates}
import org.powerapi.module.SensorChannel.{startSensor, stopSensor}
import org.powerapi.module.Sensors
import org.powerapi.module.cpu.UsageMetricsChannel.{DvfsUsageReport, subscribeDvfsUsageReport}
import org.scalamock.scalatest.MockFactory

class CpuDvfsSensorSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A CpuDvfsSensor" should "handle MonitorTick messages and sense cpu metrics for the All target" in new Bus {
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

    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1000, 100)
    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1003, 101)
    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1003, 105)
    osHelper.getTimeInStates _ expects() returning TimeInStates(Map(1l -> 20l, 2l -> 10l, 3l -> 15l))
    osHelper.getTimeInStates _ expects() returning TimeInStates(Map(1l -> 23l, 2l -> 11l, 3l -> 15l))
    osHelper.getTimeInStates _ expects() returning TimeInStates(Map(1l -> 23l, 2l -> 11l, 3l -> 19l))

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[CpuDvfsSensor].getName}").intercept({
      startSensor(muid, target, classOf[CpuDvfsSensor], Seq(eventBus, muid, target, osHelper))(eventBus)
    })
    subscribeDvfsUsageReport(muid, target)(eventBus)(testActor)

    publishMonitorTick(muid, target, tick1)(eventBus)
    var usageReport = expectMsgClass(classOf[DvfsUsageReport])
    usageReport.muid should equal(muid)
    usageReport.target should equal(target)
    usageReport.tick should equal(tick1)
    usageReport.targetRatio should equal(TargetUsageRatio((101 - 100) / ((1003 + 101) - (1000 + 100)).toDouble))
    usageReport.timeInStates should equal(TimeInStates(Map(1l -> (23l - 20l), 2l -> (11 - 10l), 3l -> (15l - 15l))))

    publishMonitorTick(muid, target, tick2)(eventBus)
    usageReport = expectMsgClass(classOf[DvfsUsageReport])
    usageReport.muid should equal(muid)
    usageReport.target should equal(target)
    usageReport.tick should equal(tick2)
    usageReport.targetRatio should equal(TargetUsageRatio((105 - 101) / ((1003 + 105) - (1003 + 101)).toDouble))
    usageReport.timeInStates should equal(TimeInStates(Map(1l -> (23l - 23l), 2l -> (11l - 11l), 3l -> (19l - 15l))))

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[CpuDvfsSensor].getName}").intercept({
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

    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1000, 100)
    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1003, 101)
    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1003, 105)
    osHelper.getTargetCpuTime _ expects Application("firefox") returning 4 + 4
    osHelper.getTargetCpuTime _ expects Application("firefox") returning 4 + 4
    osHelper.getTargetCpuTime _ expects Application("firefox") returning 5 + 6
    osHelper.getTimeInStates _ expects() returning TimeInStates(Map(1l -> 20l, 2l -> 10l, 3l -> 15l))
    osHelper.getTimeInStates _ expects() returning TimeInStates(Map(1l -> 23l, 2l -> 11l, 3l -> 15l))
    osHelper.getTimeInStates _ expects() returning TimeInStates(Map(1l -> 23l, 2l -> 11l, 3l -> 19l))

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[CpuDvfsSensor].getName}").intercept({
      startSensor(muid, target, classOf[CpuDvfsSensor], Seq(eventBus, muid, target, osHelper))(eventBus)
    })
    subscribeDvfsUsageReport(muid, target)(eventBus)(testActor)

    publishMonitorTick(muid, target, tick1)(eventBus)
    var usageReport = expectMsgClass(classOf[DvfsUsageReport])
    usageReport.muid should equal(muid)
    usageReport.target should equal(target)
    usageReport.tick should equal(tick1)
    usageReport.targetRatio should equal(TargetUsageRatio(((4 + 4) - (4 + 4)) / ((1003 + 101) - (1000 + 100)).toDouble))
    usageReport.timeInStates should equal(TimeInStates(Map(1l -> (23l - 20l), 2l -> (11 - 10l), 3l -> (15l - 15l))))

    publishMonitorTick(muid, target, tick2)(eventBus)
    usageReport = expectMsgClass(classOf[DvfsUsageReport])
    usageReport.muid should equal(muid)
    usageReport.target should equal(target)
    usageReport.tick should equal(tick2)
    usageReport.targetRatio should equal(TargetUsageRatio(((5 + 6) - (4 + 4)) / ((1003 + 105) - (1003 + 101)).toDouble))
    usageReport.timeInStates should equal(TimeInStates(Map(1l -> (23l - 23l), 2l -> (11l - 11l), 3l -> (19l - 15l))))

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[CpuDvfsSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, target, tick3)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }
}
