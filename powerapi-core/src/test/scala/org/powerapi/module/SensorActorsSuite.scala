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
package org.powerapi.module

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef, TestProbe}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.{MonitorTick, publishMonitorTick, subscribeMonitorTick, unsubscribeMonitorTick}
import org.powerapi.core.target._
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.SensorChannel.{SensorStart, SensorStop, SensorStopAll, startSensor, stopAllSensor, stopSensor}

class EmptySensor(eventBus: MessageBus, muid: UUID, target: Target, testActor: ActorRef) extends Sensor(eventBus, muid, target) {
  def init(): Unit = subscribeMonitorTick(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeMonitorTick(muid, target)(eventBus)(self)

  def handler: Actor.Receive = {
    case msg: MonitorTick => testActor forward msg
  }
}

class SensorActorsSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A Sensor actor" should "launch an exception when the messages received cannot be handled" in new Bus {
    val muid = UUID.randomUUID()
    val target1: Target = 1
    val target2: Target = 2
    val sensor1 = TestActorRef(Props(classOf[EmptySensor], eventBus, muid, target1, testActor), "sensor1")
    val sensor2 = TestActorRef(Props(classOf[EmptySensor], eventBus, muid, target2, testActor), "sensor2")

    EventFilter.warning(occurrences = 4, source = sensor1.path.toString).intercept({
      sensor1 ! SensorStop("test", muid)
      sensor1 ! SensorStopAll("test")
      sensor1 ! SensorStart("test", UUID.randomUUID(), target1, classOf[EmptySensor], Seq())
      sensor1 ! SensorStart("test", muid, All, classOf[EmptySensor], Seq())
    })

    EventFilter.info(occurrences = 1, source = sensor1.path.toString).intercept({
      sensor1 ! SensorStart("test", muid, target1, classOf[EmptySensor], Seq())
    })

    EventFilter.info(occurrences = 1, source = sensor2.path.toString).intercept({
      sensor2 ! SensorStart("test", muid, target2, classOf[EmptySensor], Seq())
    })

    EventFilter.warning(occurrences = 2, source = sensor1.path.toString).intercept({
      sensor1 ! SensorStart("test", muid, target1, classOf[EmptySensor], Seq())
      sensor1 ! SensorStop("test", UUID.randomUUID())
    })

    EventFilter.info(occurrences = 1, source = sensor1.path.toString).intercept({
      sensor1 ! SensorStop("test", muid)
    })

    EventFilter.info(occurrences = 1, source = sensor2.path.toString).intercept({
      sensor2 ! SensorStopAll("test")
    })

    Await.result(gracefulStop(sensor1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(sensor2, timeout.duration), timeout.duration)
  }

  it should "handle a specific behavior when extended" in new Bus {
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val target1: Target = 1
    val target2: Target = 2
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
    val monitorTick1 = MonitorTick("test", muid1, target1, tick1)
    val monitorTick2 = MonitorTick("test", muid2, target2, tick2)

    val sensor1 = TestActorRef(Props(classOf[EmptySensor], eventBus, muid1, target1, testActor), "sensor1")
    val sensor2 = TestActorRef(Props(classOf[EmptySensor], eventBus, muid2, target2, testActor), "sensor2")
    val watcher = TestProbe()
    watcher.watch(sensor1)
    watcher.watch(sensor2)

    EventFilter.info(occurrences = 1, source = sensor1.path.toString).intercept({
      sensor1 ! SensorStart("test", muid1, target1, classOf[EmptySensor], Seq())
    })

    EventFilter.info(occurrences = 1, source = sensor2.path.toString).intercept({
      sensor2 ! SensorStart("test", muid2, target2, classOf[EmptySensor], Seq())
    })

    publishMonitorTick(muid1, target1, tick1)(eventBus)
    var msg = expectMsgClass(classOf[MonitorTick])
    msg.muid should equal(monitorTick1.muid)
    msg.target should equal(monitorTick1.target)
    msg.tick should equal(monitorTick1.tick)
    expectNoMsg()

    EventFilter.info(occurrences = 1, source = sensor1.path.toString).intercept({
      sensor1 ! SensorStop("test", muid1)
    })

    publishMonitorTick(muid2, target2, tick2)(eventBus)
    msg = expectMsgClass(classOf[MonitorTick])
    msg.muid should equal(monitorTick2.muid)
    msg.target should equal(monitorTick2.target)
    msg.tick should equal(monitorTick2.tick)

    EventFilter.info(occurrences = 1, source = sensor2.path.toString).intercept({
      sensor2 ! SensorStopAll("test")
    })

    publishMonitorTick(muid1, target1, tick3)(eventBus)
    publishMonitorTick(muid2, target2, tick3)(eventBus)
    expectNoMsg()

    watcher.receiveN(2).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs Seq(sensor1, sensor2)

    Await.result(gracefulStop(sensor1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(sensor2, timeout.duration), timeout.duration)
  }

  "A Sensors actor" should "handle Sensor actors" in new Bus {
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val target1: Target = 1
    val target2: Target = 2
    val target3: Target = 3
    val target4: Target = 4
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

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    val watcher = TestProbe()

    EventFilter.info(occurrences = 4, start = s"sensor is started, class: ${classOf[EmptySensor].getName}").intercept({
      startSensor(muid1, target1, classOf[EmptySensor], Seq(eventBus, muid1, target1, testActor))(eventBus)
      startSensor(muid1, target2, classOf[EmptySensor], Seq(eventBus, muid1, target2, testActor))(eventBus)
      startSensor(muid2, target3, classOf[EmptySensor], Seq(eventBus, muid2, target3, testActor))(eventBus)
      startSensor(muid2, target4, classOf[EmptySensor], Seq(eventBus, muid2, target4, testActor))(eventBus)
    })

    EventFilter.warning(occurrences = 1, start = s"this sensor is started already, class: ${classOf[EmptySensor].getName}").intercept({
      startSensor(muid1, target1, classOf[EmptySensor], Seq(eventBus, muid1, target1, testActor))(eventBus)
    })

    val sensorChildren = sensors.children.seq
    sensorChildren foreach watcher.watch

    publishMonitorTick(muid1, target1, tick1)(eventBus)
    var msg = expectMsgClass(classOf[MonitorTick])
    msg.muid should equal(muid1)
    msg.target should equal(target1)
    msg.tick should equal(tick1)

    publishMonitorTick(muid1, target2, tick1)(eventBus)
    msg = expectMsgClass(classOf[MonitorTick])
    msg.muid should equal(muid1)
    msg.target should equal(target2)
    msg.tick should equal(tick1)

    publishMonitorTick(muid2, target3, tick2)(eventBus)
    msg = expectMsgClass(classOf[MonitorTick])
    msg.muid should equal(muid2)
    msg.target should equal(target3)
    msg.tick should equal(tick2)

    stopSensor(muid1)(eventBus)
    stopAllSensor(eventBus)

    watcher.receiveN(4).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs sensorChildren

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }
}
