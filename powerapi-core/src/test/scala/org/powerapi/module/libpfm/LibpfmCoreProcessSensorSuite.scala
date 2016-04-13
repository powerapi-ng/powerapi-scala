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
package org.powerapi.module.libpfm

import java.util.UUID

import scala.collection.BitSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.core.target.{All, Application, Process}
import org.powerapi.core.{MessageBus, OSHelper, Thread, Tick}
import org.powerapi.module.SensorChannel.startSensor
import org.powerapi.module.Sensors
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, PCReport, subscribePCReport}
import org.scalamock.scalatest.MockFactory

class LibpfmCoreProcessSensorSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)
  val topology = Map(0 -> Set(0), 1 -> Set(1))
  val events = Set("event", "event1")

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A LibpfmCoreProcessSensor" should "handle MonitorTick messages and sense HW counter values for the Process/Application/Container targets" in new Bus {
    val configuration = BitSet()
    val libpfmHelper = mock[LibpfmHelper]
    val muid = UUID.randomUUID()
    val target = Application("firefox")
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
    val tick4 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 3000
    }

    val osHelper = mock[OSHelper]

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")

    libpfmHelper.disablePC _ expects * anyNumberOfTimes() returning true
    libpfmHelper.closePC _ expects * anyNumberOfTimes() returning true

    libpfmHelper.resetPC _ expects * repeat 3 * topology.values.flatten.size * events.size returning true
    libpfmHelper.enablePC _ expects * repeat 3 * topology.values.flatten.size * events.size returning true
    osHelper.getProcesses _ expects target once() returning Set(Process(10))
    osHelper.getThreads _ expects Process(10) once() returning Set(Thread(11), Thread(12))

    val fds = (0 until 3 * topology.values.flatten.size * events.size).iterator
    for {
      core: Int <- topology.keys
      index: Int <- topology(core)
      event: String <- events
      id: Int <- Set(10, 11, 12)
    } libpfmHelper.configurePC _ expects(TCID(id, index), configuration, event) returning Some(fds.next)
    libpfmHelper.readPC _ expects * repeat 3 * topology.values.flatten.size * events.size returning Array(1l, 1l, 1l)

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[LibpfmCoreProcessSensor].getName}").intercept({
      startSensor(muid, target, classOf[LibpfmCoreProcessSensor], Seq(eventBus, muid, target, osHelper, libpfmHelper, timeout, topology, configuration, events, true))(eventBus)
    })
    subscribePCReport(muid, target)(eventBus)(testActor)

    osHelper.getProcesses _ expects target once() returning Set(Process(10))
    osHelper.getThreads _ expects Process(10) once() returning Set(Thread(11), Thread(12))
    for (i <- 0 until 3 * topology.values.flatten.size * events.size) {
      libpfmHelper.readPC _ expects i returning Array[Long](i + 5, 2, 2)
      libpfmHelper.scale _ expects where {
        (now: Array[Long], old: Array[Long]) => now.deep == Array[Long](i + 5, 2, 2).deep && old.deep == Array[Long](1, 1, 1).deep
      } returning Some(i + 5 - 1)
    }

    publishMonitorTick(muid, target, tick1)(eventBus)
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, _target, wrappers, _) =>
        _target should equal(target)
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size * 3))

        wrappers.groupBy(wrapper => (wrapper.core, wrapper.event)).foreach {
          case ((core, event), _wrappers) =>
            val pcs = Await.result(Future.sequence(_wrappers.head.values), timeout.duration).asInstanceOf[List[HWCounter]]
            val values = Map[(Int, String), Long]((0, "event") -> 15, (0, "event1") -> 24, (1, "event") -> 33, (1, "event1") -> 42)

            pcs.map(_.value).sum should equal(values(((core, event))))
        }
    }

    osHelper.getProcesses _ expects target once() returning Set(Process(10))
    osHelper.getThreads _ expects Process(10) once() returning Set()
    for (i <- 0 until 3 * events.size by 3) {
      libpfmHelper.readPC _ expects i returning Array[Long](i + 50, 4, 4)
      libpfmHelper.scale _ expects where {
        (now: Array[Long], old: Array[Long]) => now.deep == Array[Long](i + 50, 4, 4).deep && old.deep == Array[Long](i + 5, 2, 2).deep
      } returning Some((i + 50) - (i + 5))
    }
    for (i <- 3 * events.size until 3 * events.size * topology.values.flatten.size by 3) {
      libpfmHelper.readPC _ expects i returning Array[Long](i + 5, 2, 2)
      libpfmHelper.scale _ expects where {
        (now: Array[Long], old: Array[Long]) => now.deep == Array[Long](i + 5, 2, 2).deep && old.deep == Array[Long](i + 5, 2, 2).deep
      } returning None
    }

    publishMonitorTick(muid, target, tick2)(eventBus)
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, _target, wrappers, _) =>
        _target should equal(target)
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        wrappers.groupBy(wrapper => (wrapper.core, wrapper.event)).foreach {
          case ((core, event), _wrappers) =>
            val pcs = Await.result(Future.sequence(_wrappers.head.values), timeout.duration).asInstanceOf[List[HWCounter]]
            val values = Map[(Int, String), Long]((0, "event") -> 45, (0, "event1") -> 45, (1, "event") -> 0, (1, "event1") -> 0)
            val periods = Map[(Int, String), Double]((0, "event") -> 2, (0, "event1") -> 2, (1, "event") -> Double.NaN, (1, "event1") -> Double.NaN)

            pcs.map(_.value).sum should equal(values((core, event)))
        }
    }

    osHelper.getProcesses _ expects target anyNumberOfTimes() returning Set()
    publishMonitorTick(muid, target, tick3)(eventBus)

    publishMonitorTick(muid, target, tick4)(eventBus)
    expectNoMsg()

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[LibpfmCoreProcessSensor].getName}").intercept({
      startSensor(muid, All, classOf[LibpfmCoreProcessSensor], Seq(eventBus, muid, All, osHelper, libpfmHelper, timeout, topology, configuration, events, true))(eventBus)
    })
    subscribePCReport(muid, All)(eventBus)(testActor)

    publishMonitorTick(muid, All, tick1)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }
}
