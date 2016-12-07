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
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.core.target.{All, Process}
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.SensorChannel.{startSensor, stopSensor}
import org.powerapi.module.Sensors
import org.powerapi.module.libpfm.PerformanceCounterChannel.{PCReport, subscribePCReport}
import org.scalamock.scalatest.MockFactory

class LibpfmCoreSensorSuite extends UnitTest with MockFactory {

  val timeout = Timeout(20.seconds)
  val topology = Map(0 -> Set(0, 1), 1 -> Set(2, 3))
  val events = Set("event", "event1")

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A LibpfmCoreSensor" should "handle MonitorTick messages and sense HW counter values for the All target" in new Bus {
    val configuration = BitSet()
    val helper = mock[LibpfmHelper]
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
    val fds = (0 until topology.values.flatten.size * events.size).iterator

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")

    helper.disablePC _ expects * anyNumberOfTimes() returning true
    helper.closePC _ expects * anyNumberOfTimes() returning true

    helper.resetPC _ expects * repeat topology.values.flatten.size * events.size returning true
    helper.enablePC _ expects * repeat topology.values.flatten.size * events.size returning true

    for {
      core: Int <- topology.keys
      index: Int <- topology(core)
      event: String <- events
    } helper.configurePC _ expects(CID(index), configuration, event) returning Some(fds.next)
    helper.readPC _ expects * repeat topology.values.flatten.size * events.size returning Array(1l, 1l, 1l)

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[LibpfmCoreSensor].getName}").intercept({
      startSensor(muid, target, classOf[LibpfmCoreSensor], Seq(eventBus, muid, target, helper, timeout, topology, configuration, events))(eventBus)
    })
    subscribePCReport(muid, target)(eventBus)(testActor)

    for (i <- 0 until topology.values.flatten.size * events.size) {
      helper.readPC _ expects i returning Array[Long](i + 5, 2, 2)
      helper.scale _ expects where {
        (now: Array[Long], old: Array[Long]) => now.deep == Array[Long](i + 5, 2, 2).deep && old.deep == Array[Long](1, 1, 1).deep
      } returning Some(i + 5 - 1)
    }

    val results = Map[(Int, String), Long]((0, "event") -> 10, (0, "event1") -> 12, (1, "event") -> 18, (1, "event1") -> 20)
    publishMonitorTick(muid, All, tick1)(eventBus)
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, All, values, _) =>
        values.size should equal(topology.size)

        for (value <- values) {
          for ((event, counters)  <- value._2) {
            counters.map(_.value).sum should equal(results((value._1, event)))
          }
        }
      case _ =>
        {}
    }

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[LibpfmCoreSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, All, tick2)(eventBus)
    expectNoMsg()

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[LibpfmCoreSensor].getName}").intercept({
      startSensor(muid, Process(1), classOf[LibpfmCoreSensor], Seq(eventBus, muid, Process(1), helper, timeout, topology, configuration, events))(eventBus)
    })
    subscribePCReport(muid, Process(1))(eventBus)(testActor)

    publishMonitorTick(muid, Process(1), tick1)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }
}
