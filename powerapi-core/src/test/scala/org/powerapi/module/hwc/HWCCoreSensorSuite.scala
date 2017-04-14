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
package org.powerapi.module.hwc

import java.util.UUID

import akka.actor.Props
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.core.target.{All, Container}
import org.powerapi.core.{MessageBus, OSHelper, Tick}
import org.powerapi.module.SensorChannel.{startSensor, stopSensor}
import org.powerapi.module.Sensors
import org.powerapi.module.hwc.HWCChannel.{HWC, HWCReport, subscribeHWCReport}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration.DurationInt

class HWCCoreSensorSuite extends UnitTest with MockFactory {

  val timeout = Timeout(20.seconds)

  val basepath = getClass.getResource("/").getPath

  val events = Seq("EVENT:FIXC1", "EVENT:FIXC2")

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A HWCCoreSensor" should "handle MonitorTick messages and sense HW counter values for the All target" in new Bus {
    val osHelper = stub[OSHelper]
    val likwidHelper = mock[LikwidHelper]
    val cHelper = mock[CHelper]

    val muid = UUID.randomUUID()
    val target = All
    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")

    likwidHelper.getCpuTopology _ expects() returning CpuTopology(1, 2, 1, Seq(HWThread(0, 0, 0, 0, 0), HWThread(1, 1, 0, 1, 1)), Seq())
    likwidHelper.perfmonInit _ expects Seq(0, 1) returning 1
    likwidHelper.perfmonAddEventSet _ expects s"EVENT:FIXC1:PERF_PID=${java.lang.Long.toHexString(-1)}:PERF_FLAGS=${java.lang.Long.toHexString(0)},EVENT:FIXC2:PERF_PID=${java.lang.Long.toHexString(-1)}:PERF_FLAGS=${java.lang.Long.toHexString(0)}" returning 10
    likwidHelper.perfmonSetupCounters _ expects 10  returning 1
    likwidHelper.perfmonStartCounters _ expects() returning 1
    likwidHelper.perfmonGetLastResult _ expects (10, 0, 0) returning 100
    likwidHelper.perfmonGetLastResult _ expects (10, 0, 1) returning 200
    likwidHelper.perfmonGetLastResult _ expects (10, 1, 0) returning 1000
    likwidHelper.perfmonGetLastResult _ expects (10, 1, 1) returning 2000
    likwidHelper.perfmonStopCounters _ expects() returning 1
    likwidHelper.perfmonFinalize _ expects()

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[HWCCoreSensor].getName}").intercept({
      startSensor(muid, target, classOf[HWCCoreSensor], Seq(eventBus, muid, target, osHelper, likwidHelper, cHelper, events))(eventBus)
    })
    subscribeHWCReport(muid, target)(eventBus)(testActor)

    publishMonitorTick(muid, All, tick1)(eventBus)

    expectMsgClass(classOf[HWCReport]) match {
      case HWCReport(_, _, All, values, _) =>
        values should contain theSameElementsAs Seq(
          HWC(HWThread(0, 0, 0, 0, 0), "EVENT:FIXC1", 100),
          HWC(HWThread(1, 1, 0, 1, 1), "EVENT:FIXC1", 200),
          HWC(HWThread(0, 0, 0, 0, 0), "EVENT:FIXC2", 1000),
          HWC(HWThread(1, 1, 0, 1, 1), "EVENT:FIXC2", 2000)
        )
      case _ =>
    }

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[HWCCoreSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, All, tick2)(eventBus)
    expectNoMsg()
  }

  it should "handle MonitorTick messages and sense HW counter values for the Container target" in new Bus {
    val osHelper = mock[OSHelper]
    val likwidHelper = mock[LikwidHelper]
    val cHelper = mock[CHelper]

    val muid = UUID.randomUUID()
    val target = Container("123456", "test")
    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")

    osHelper.cgroupMntPoint _ expects "perf_event" returning Some(s"${basepath}sys/fs/cgroup/perf_event")
    cHelper.open _ expects (s"${basepath}sys/fs/cgroup/perf_event/docker/12345678900", 0) returning 22

    likwidHelper.getCpuTopology _ expects() returning CpuTopology(1, 2, 1, Seq(HWThread(0, 0, 0, 0, 0), HWThread(1, 1, 0, 1, 1)), Seq())
    likwidHelper.perfmonInit _ expects Seq(0, 1) returning 1
    likwidHelper.perfmonAddEventSet _ expects s"EVENT:FIXC1:PERF_PID=${java.lang.Long.toHexString(22)}:PERF_FLAGS=${java.lang.Long.toHexString(4)},EVENT:FIXC2:PERF_PID=${java.lang.Long.toHexString(22)}:PERF_FLAGS=${java.lang.Long.toHexString(4)}" returning 10
    likwidHelper.perfmonSetupCounters _ expects 10  returning 1
    likwidHelper.perfmonStartCounters _ expects() returning 1
    likwidHelper.perfmonGetLastResult _ expects (10, 0, 0) returning 100
    likwidHelper.perfmonGetLastResult _ expects (10, 0, 1) returning 200
    likwidHelper.perfmonGetLastResult _ expects (10, 1, 0) returning 1000
    likwidHelper.perfmonGetLastResult _ expects (10, 1, 1) returning 2000
    likwidHelper.perfmonStopCounters _ expects() returning 1
    likwidHelper.perfmonFinalize _ expects()

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[HWCCoreSensor].getName}").intercept({
      startSensor(muid, target, classOf[HWCCoreSensor], Seq(eventBus, muid, target, osHelper, likwidHelper, cHelper, events))(eventBus)
    })
    subscribeHWCReport(muid, target)(eventBus)(testActor)

    publishMonitorTick(muid, target, tick1)(eventBus)

    expectMsgClass(classOf[HWCReport]) match {
      case HWCReport(_, _, c: Container, values, _) =>
        c should equal(target)
        values should contain theSameElementsAs Seq(
          HWC(HWThread(0, 0, 0, 0, 0), "EVENT:FIXC1", 100),
          HWC(HWThread(1, 1, 0, 1, 1), "EVENT:FIXC1", 200),
          HWC(HWThread(0, 0, 0, 0, 0), "EVENT:FIXC2", 1000),
          HWC(HWThread(1, 1, 0, 1, 1), "EVENT:FIXC2", 2000)
        )
      case _ =>
    }

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[HWCCoreSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, target, tick2)(eventBus)
    expectNoMsg()
  }
}
