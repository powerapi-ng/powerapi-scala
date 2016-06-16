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

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.actor.Props
import akka.testkit.{EventFilter, TestActorRef}
import akka.pattern.gracefulStop
import akka.util.Timeout
import com.google.protobuf.ByteString
import org.powerapi.UnitTest
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.core.target.{Application, Process}
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.module.SensorChannel.{startSensor, stopSensor}
import org.powerapi.module.Sensors
import org.powerapi.module.libpfm.PayloadProtocol.{MapEntry, Payload}
import org.powerapi.module.libpfm.PCInterruptionChannel.{InterruptionHWCounter, InterruptionPCReport, subscribeInterruptionPCReport}

class LibpfmInterruptionCoreSensorSuite extends UnitTest {

  val timeout = Timeout(20.seconds)
  val topology = Map(0 -> Set(0, 1), 1 -> Set(2, 3))
  val events = Set("event", "event1")

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A LibpfmInterruptionCoreSensor" should "handle MonitorTick messages and exploit extended ticks from a PowerAPI agent" in new Bus {
    val muid = UUID.randomUUID()
    val target = Process(10)

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    subscribeInterruptionPCReport(muid, target)(eventBus)(testActor)

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[LibpfmInterruptionCoreSensor].getName}").intercept({
      startSensor(muid, target, classOf[LibpfmInterruptionCoreSensor], Seq(eventBus, muid, target, topology, events))(eventBus)
    })

    val payload1 = Payload.newBuilder().setCore(0)
      .setPid(target.pid)
      .setTid(10)
      .setTimestamp(System.nanoTime())
      .addCounters(0, MapEntry.newBuilder().setKey("event").setValue(1000))
      .addCounters(1, MapEntry.newBuilder().setKey("event1").setValue(10))
      .addTraces("c")
      .addTraces("b")
      .addTraces("a")
      .addTraces("main")
      .build()
    val payload2 = Payload.newBuilder().setCore(0)
      .setPid(target.pid)
      .setTid(11)
      .setTimestamp(System.nanoTime() + 1000000000)
      .addCounters(0, MapEntry.newBuilder().setKey("event").setValue(1100))
      .addCounters(1, MapEntry.newBuilder().setKey("event1").setValue(11))
      .addTraces("d")
      .addTraces("c")
      .addTraces("b")
      .addTraces("a")
      .addTraces("main")
      .build()
    val payload3 = Payload.newBuilder().setCore(1)
      .setPid(target.pid)
      .setTid(13)
      .setTimestamp(System.nanoTime() + 2000000000)
      .addCounters(0, MapEntry.newBuilder().setKey("event").setValue(1300))
      .addCounters(1, MapEntry.newBuilder().setKey("event1").setValue(13))
      .addTraces("z")
      .addTraces("main")
      .build()
    val payload4 = Payload.newBuilder().setCore(1)
      .setPid(target.pid)
      .setTid(13)
      .setTimestamp(0l)
      .addCounters(0, MapEntry.newBuilder().setKey("event").setValue(0))
      .addCounters(1, MapEntry.newBuilder().setKey("event1").setValue(0))
      .build()
    val payload5 = Payload.newBuilder().setCore(1)
      .setPid(target.pid)
      .setTid(14)
      .setTimestamp(0l)
      .addCounters(0, MapEntry.newBuilder().setKey("event").setValue(0))
      .addCounters(1, MapEntry.newBuilder().setKey("event1").setValue(0))
      .build()
    val payload6 = Payload.newBuilder().setCore(1)
      .setPid(target.pid)
      .setTid(15)
      .setTimestamp(System.nanoTime() + 3000000000l)
      .addCounters(0, MapEntry.newBuilder().setKey("event").setValue(1500))
      .addCounters(1, MapEntry.newBuilder().setKey("event1").setValue(15))
      .addTraces("c")
      .addTraces("b")
      .addTraces("main")
      .build()
    val payload7 = Payload.newBuilder().setCore(2)
      .setPid(target.pid)
      .setTid(16)
      .setTimestamp(System.nanoTime() + 4000000000l)
      .addCounters(0, MapEntry.newBuilder().setKey("event").setValue(1600))
      .addCounters(1, MapEntry.newBuilder().setKey("event1").setValue(16))
      .addTraces("d")
      .addTraces("main")
      .build()

    val tick1 = AgentTick("test", payload1.getTimestamp, payload1)
    val tick2 = AgentTick("test", payload2.getTimestamp, payload2)
    val tick3 = AgentTick("test", payload3.getTimestamp, payload3)
    val tick4 = AgentTick("test", payload4.getTimestamp, payload4)
    val tick5 = AgentTick("test", payload5.getTimestamp, payload5)
    val tick6 = AgentTick("test", payload6.getTimestamp, payload6)
    val tick7 = AgentTick("test", payload7.getTimestamp, payload7)

    publishMonitorTick(muid, target, tick1)(eventBus)
    expectMsgClass(classOf[InterruptionPCReport]) match {
      case InterruptionPCReport(_, _muid, _target, wrappers, _tick) =>
        _muid should equal(muid)
        _target should equal(target)
        _tick.timestamp should equal(tick1.timestamp)
        wrappers.size should equal(events.size)
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 10, "main.a.b.c", 1000, true))
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event1").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 10, "main.a.b.c", 10, true))
    }

    publishMonitorTick(muid, target, tick2)(eventBus)
    expectMsgClass(classOf[InterruptionPCReport]) match {
      case InterruptionPCReport(_, _muid, _target, wrappers, _tick) =>
        _muid should equal(muid)
        _target should equal(target)
        _tick.timestamp should equal(tick2.timestamp)
        wrappers.size should equal(events.size)
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 11, "main.a.b.c.d", 1100, true))
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event1").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 11, "main.a.b.c.d", 11, true))
    }

    publishMonitorTick(muid, target, tick3)(eventBus)
    expectMsgClass(classOf[InterruptionPCReport]) match {
      case InterruptionPCReport(_, _muid, _target, wrappers, _tick) =>
        _muid should equal(muid)
        _target should equal(target)
        _tick.timestamp should equal(tick3.timestamp)
        wrappers.size should equal(events.size)
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 11, "main.a.b.c.d", 1100, false), InterruptionHWCounter(1, 13, "main.z", 1300, true))
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event1").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 11, "main.a.b.c.d", 11, false),  InterruptionHWCounter(1, 13, "main.z", 13, true))
    }

    publishMonitorTick(muid, target, tick4)(eventBus)
    publishMonitorTick(muid, target, tick5)(eventBus)
    publishMonitorTick(muid, target, new Tick { val topic = ""; val timestamp = 0l })(eventBus)
    expectNoMsg()

    publishMonitorTick(muid, target, tick6)(eventBus)
    expectMsgClass(classOf[InterruptionPCReport]) match {
      case InterruptionPCReport(_, _muid, _target, wrappers, _tick) =>
        _muid should equal(muid)
        _target should equal(target)
        _tick.timestamp should equal(tick6.timestamp)
        wrappers.size should equal(events.size)
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 11, "main.a.b.c.d", 1100, false), InterruptionHWCounter(1, 15, "main.b.c", 1500, true))
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event1").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 11, "main.a.b.c.d", 11, false),  InterruptionHWCounter(1, 15, "main.b.c", 15, true))
    }

    publishMonitorTick(muid, target, tick7)(eventBus)
    expectMsgClass(classOf[InterruptionPCReport]) match {
      case InterruptionPCReport(_, _muid, _target, wrappers, _tick) =>
        _muid should equal(muid)
        _target should equal(target)
        _tick.timestamp should equal(tick7.timestamp)
        wrappers.size should equal(events.size * 2)
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 11, "main.a.b.c.d", 1100, false), InterruptionHWCounter(1, 15, "main.b.c", 1500, false))
        wrappers.filter(wrapper => wrapper.core == 0 && wrapper.event == "event1").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(0, 11, "main.a.b.c.d", 11, false),  InterruptionHWCounter(1, 15, "main.b.c", 15, false))
        wrappers.filter(wrapper => wrapper.core == 1 && wrapper.event == "event").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(2, 16, "main.d", 1600, true))
        wrappers.filter(wrapper => wrapper.core == 1 && wrapper.event == "event1").head.values should
          contain theSameElementsAs Seq(InterruptionHWCounter(2, 16, "main.d", 16, true))
    }

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[LibpfmInterruptionCoreSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }
}
