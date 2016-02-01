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

import akka.actor.{ActorSystem, Props, Terminated}
import akka.pattern.gracefulStop
import akka.util.Timeout
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import java.util.UUID
import org.powerapi.UnitTest
import org.powerapi.core.{GlobalCpuTime, TimeInStates, OSHelper, MessageBus, Thread}
import org.powerapi.core.target.{TargetUsageRatio, Application, Process}
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.MonitorChannel.MonitorTick
import org.powerapi.module.SensorChannel.{MonitorStopAll, MonitorStop}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{PCReport, subscribePCReport}
import org.scalamock.scalatest.MockFactory
import scala.collection.BitSet
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class LibpfmCoreProcessSensorSuite(system: ActorSystem) extends UnitTest(system) with MockFactory {

  def this() = this(ActorSystem("LibpfmCoreProcessSensorSuite"))

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  val topology = Map(0 -> Set(0), 1 -> Set(1))
  val events = Set("event", "event1")

  "A LibpfmCoreProcessSensor" should "aggregate the performance counters per core/event/id" in new Bus {
    val configuration = BitSet()
    val libpfmHelper = mock[LibpfmHelper]
    val muid1 = UUID.randomUUID()
    val osHelper = new OSHelper {
      override def getThreads(process: Process): Set[Thread] = Set()

      override def getTimeInStates: TimeInStates = TimeInStates(Map())

      override def getGlobalCpuPercent(muid: UUID): TargetUsageRatio = TargetUsageRatio(0.0)

      override def getCPUFrequencies: Set[Long] = Set()

      override def getProcessCpuPercent(muid: UUID, process: Process): TargetUsageRatio = TargetUsageRatio(0.0)

      override def getProcessCpuTime(process: Process): Option[Long] = Some(0)

      override def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0l, 0l)

      override def getProcesses(application: Application): Set[Process] = Set(Process(10), Process(11))
    }

    val sensor = TestActorRef(Props(classOf[LibpfmCoreProcessSensor], eventBus, osHelper, libpfmHelper, Timeout(1.seconds), topology, configuration, events, true), "core-process-sensor1")(system)
    subscribePCReport(eventBus)(testActor)

    libpfmHelper.resetPC _ expects * anyNumberOfTimes() returning true
    libpfmHelper.enablePC _ expects * anyNumberOfTimes() returning true
    libpfmHelper.disablePC _ expects * anyNumberOfTimes() returning true
    libpfmHelper.closePC _ expects * anyNumberOfTimes() returning true

    libpfmHelper.configurePC _ expects(TCID(1, 0), configuration, "event") returning Some(0)
    libpfmHelper.configurePC _ expects(TCID(1, 0), configuration, "event1") returning Some(1)
    libpfmHelper.configurePC _ expects(TCID(1, 1), configuration, "event") returning Some(2)
    libpfmHelper.configurePC _ expects(TCID(1, 1), configuration, "event1") returning Some(3)
    libpfmHelper.readPC _ expects * repeat 4 returning Array(1, 1, 1)
    sensor ! MonitorTick("monitor", muid1, Process(1), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Process(1))
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for (wrapper <- wrappers) {
          Future.sequence(wrapper.values) onSuccess {
            case coreValues: List[Long] => {
              val aggValue = coreValues.foldLeft(0l)((acc, value) => acc + value)
              aggValue should equal(0l)
            }
          }
        }
      }
    }
    libpfmHelper.readPC _ expects 0 returning Array(5, 2, 2)
    libpfmHelper.readPC _ expects 1 returning Array(6, 2, 2)
    libpfmHelper.readPC _ expects 2 returning Array(7, 2, 2)
    libpfmHelper.readPC _ expects 3 returning Array(8, 2, 2)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(5l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(4)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(6l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(5)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(7l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(6)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(8l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(7)
    var results = Map[(Int, String), Long]((0, "event") -> 4, (0, "event1") -> 5, (1, "event") -> 6, (1, "event1") -> 7)
    sensor ! MonitorTick("monitor", muid1, Process(1), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Process(1))
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for ((core, _) <- topology) {
          for (event <- events) {
            Future.sequence(wrappers.filter(wrapper => wrapper.core == core && wrapper.event == event).head.values) onSuccess {
              case values: List[Long] => values.foldLeft(0l)((acc, value) => acc + value) should equal(results(core, event))
            }
          }
        }
      }
    }

    libpfmHelper.configurePC _ expects(TCID(10, 0), configuration, "event") returning Some(4)
    libpfmHelper.configurePC _ expects(TCID(11, 0), configuration, "event") returning Some(5)
    libpfmHelper.configurePC _ expects(TCID(10, 0), configuration, "event1") returning Some(6)
    libpfmHelper.configurePC _ expects(TCID(11, 0), configuration, "event1") returning Some(7)
    libpfmHelper.configurePC _ expects(TCID(10, 1), configuration, "event") returning Some(8)
    libpfmHelper.configurePC _ expects(TCID(11, 1), configuration, "event") returning Some(9)
    libpfmHelper.configurePC _ expects(TCID(10, 1), configuration, "event1") returning Some(10)
    libpfmHelper.configurePC _ expects(TCID(11, 1), configuration, "event1") returning Some(11)
    libpfmHelper.readPC _ expects * repeat 8 returning Array(1, 1, 1)
    sensor ! MonitorTick("monitor", muid1, Application("app"), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Application("app"))
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(2 * topology(0).size))

        for (wrapper <- wrappers) {
          Future.sequence(wrapper.values) onSuccess {
            case coreValues: List[Long] => {
              val aggValue = coreValues.foldLeft(0l)((acc, value) => acc + value)
              aggValue should equal(0l)
            }
          }
        }
      }
    }
    libpfmHelper.readPC _ expects 4 returning Array(9, 2, 2)
    libpfmHelper.readPC _ expects 5 returning Array(10, 2, 2)
    libpfmHelper.readPC _ expects 6 returning Array(11, 2, 2)
    libpfmHelper.readPC _ expects 7 returning Array(12, 2, 2)
    libpfmHelper.readPC _ expects 8 returning Array(13, 2, 2)
    libpfmHelper.readPC _ expects 9 returning Array(14, 2, 2)
    libpfmHelper.readPC _ expects 10 returning Array(15, 2, 2)
    libpfmHelper.readPC _ expects 11 returning Array(16, 2, 2)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(9l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(8)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(10l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(9)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(11l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(10)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(12l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(11)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(13l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(12)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(14l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(13)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(15l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(14)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(16l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(15)
    results = Map[(Int, String), Long]((0, "event") -> 17, (0, "event1") -> 21, (1, "event") -> 25, (1, "event1") -> 29)
    sensor ! MonitorTick("monitor", muid1, Application("app"), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Application("app"))
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(2 * topology(0).size))

        for ((core, _) <- topology) {
          for (event <- events) {
            Future.sequence(wrappers.filter(wrapper => wrapper.core == core && wrapper.event == event).head.values) onSuccess {
              case values: List[Long] => values.foldLeft(0l)((acc, value) => acc + value) should equal(results(core, event))
            }
          }
        }
      }
    }

    Await.result(gracefulStop(sensor, timeout.duration), timeout.duration)
  }

  it should "close correctly the resources" in new Bus {
    val configuration = BitSet()
    val libpfmHelper = mock[LibpfmHelper]
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val osHelper = new OSHelper {
      override def getThreads(process: Process): Set[Thread] = Set()

      override def getTimeInStates: TimeInStates = TimeInStates(Map())

      override def getGlobalCpuPercent(muid: UUID): TargetUsageRatio = TargetUsageRatio(0.0)

      override def getCPUFrequencies: Set[Long] = Set()

      override def getProcessCpuPercent(muid: UUID, process: Process): TargetUsageRatio = TargetUsageRatio(0.0)

      override def getProcessCpuTime(process: Process): Option[Long] = Some(0)

      override def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0l, 0l)

      override def getProcesses(application: Application): Set[Process] = Set(Process(10), Process(11))
    }
    val reaper = TestProbe()(system)

    val sensor = TestActorRef(Props(classOf[LibpfmCoreProcessSensor], eventBus, osHelper, libpfmHelper, Timeout(1.seconds), topology, configuration, events, true), "core-process-sensor2")(system)
    subscribePCReport(eventBus)(testActor)

    libpfmHelper.resetPC _ expects * anyNumberOfTimes() returning true
    libpfmHelper.enablePC _ expects * anyNumberOfTimes() returning true
    libpfmHelper.disablePC _ expects * anyNumberOfTimes() returning true
    libpfmHelper.closePC _ expects * anyNumberOfTimes() returning true

    libpfmHelper.configurePC _ expects(TCID(1, 0), configuration, "event") returning Some(0)
    libpfmHelper.configurePC _ expects(TCID(1, 0), configuration, "event1") returning Some(1)
    libpfmHelper.configurePC _ expects(TCID(1, 1), configuration, "event") returning Some(2)
    libpfmHelper.configurePC _ expects(TCID(1, 1), configuration, "event1") returning Some(3)
    libpfmHelper.readPC _ expects * repeat 4 returning Array(1, 1, 1)
    sensor ! MonitorTick("monitor", muid1, Process(1), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Process(1))
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for (wrapper <- wrappers) {
          Future.sequence(wrapper.values) onSuccess {
            case coreValues: List[Long] => {
              val aggValue = coreValues.foldLeft(0l)((acc, value) => acc + value)
              aggValue should equal(0l)
            }
          }
        }
      }
    }
    libpfmHelper.configurePC _ expects(TCID(1, 0), configuration, "event") returning Some(0)
    libpfmHelper.configurePC _ expects(TCID(1, 0), configuration, "event1") returning Some(1)
    libpfmHelper.configurePC _ expects(TCID(1, 1), configuration, "event") returning Some(2)
    libpfmHelper.configurePC _ expects(TCID(1, 1), configuration, "event1") returning Some(3)
    libpfmHelper.readPC _ expects * repeat 4 returning Array(1, 1, 1)
    sensor ! MonitorTick("monitor", muid2, Process(1), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Process(1))
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for (wrapper <- wrappers) {
          Future.sequence(wrapper.values) onSuccess {
            case coreValues: List[Long] => {
              val aggValue = coreValues.foldLeft(0l)((acc, value) => acc + value)
              aggValue should equal(0l)
            }
          }
        }
      }
    }
    var children = sensor.children.toArray.clone().filter(_.path.name.contains(muid1.toString))
    children.foreach(child => reaper watch child)
    children.size should equal(4)
    sensor ! MonitorStop("sensor", muid1)
    for(_ <- 0 until children.size) {
      reaper.expectMsgClass(classOf[Terminated])
    }

    children = sensor.children.toArray.clone()
    children.foreach(child => reaper watch child)
    children.size should equal(4)
    sensor ! MonitorStopAll("sensor")
    for(_ <- 0 until children.size) {
      reaper.expectMsgClass(classOf[Terminated])
    }

    Await.result(gracefulStop(sensor, timeout.duration), timeout.duration)
  }
}
