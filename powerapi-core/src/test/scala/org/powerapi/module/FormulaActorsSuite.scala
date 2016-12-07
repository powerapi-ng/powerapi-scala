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
import akka.event.LoggingReceive
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef, TestProbe}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.target.{All, Target}
import org.powerapi.core.{Channel, Message, MessageBus, Tick}
import org.powerapi.module.FormulaChannel.{FormulaStart, FormulaStop, FormulaStopAll, startFormula, stopAllFormula, stopFormula}

object MockChannel extends Channel {

  type M = MessageMock

  def publishSensorReportMock(muid: UUID, target: Target, tick: Tick): MessageBus => Unit = {
    publish(SensorReportMock(sensorReportMockTopic(muid, target), muid, target, tick))
  }

  def subscribeSensorReportMock(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(sensorReportMockTopic(muid, target))
  }

  def unsubscribeSensorReportMock(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(sensorReportMockTopic(muid, target))
  }

  def sensorReportMockTopic(muid: UUID, target: Target): String = {
    s"sensormock:$muid:$target"
  }

  trait MessageMock extends Message

  case class SensorReportMock(topic: String, muid: UUID, target: Target, tick: Tick) extends MessageMock

}

class EmptyFormula(eventBus: MessageBus, muid: UUID, target: Target, testActor: ActorRef) extends Formula(eventBus, muid, target) {
  def init(): Unit = MockChannel.subscribeSensorReportMock(muid, target)(eventBus)(self)

  def terminate(): Unit = MockChannel.unsubscribeSensorReportMock(muid, target)(eventBus)(self)

  def handler: Actor.Receive = LoggingReceive {
    case msg: MockChannel.SensorReportMock => testActor forward msg
  }
}

class FormulaActorsSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A Formula actor" should "launch an exception when the messages received cannot be handled" in new Bus {
    val muid = UUID.randomUUID()
    val target1: Target = 1
    val target2: Target = 2
    val formula1 = TestActorRef(Props(classOf[EmptyFormula], eventBus, muid, target1, testActor), "formula1")
    val formula2 = TestActorRef(Props(classOf[EmptyFormula], eventBus, muid, target2, testActor), "formula2")

    EventFilter.warning(occurrences = 4, source = formula1.path.toString).intercept({
      formula1 ! FormulaStop("test", muid)
      formula1 ! FormulaStopAll("test")
      formula1 ! FormulaStart("test", UUID.randomUUID(), target1, classOf[EmptyFormula], Seq())
      formula1 ! FormulaStart("test", muid, All, classOf[EmptyFormula], Seq())
    })

    EventFilter.info(occurrences = 1, source = formula1.path.toString).intercept({
      formula1 ! FormulaStart("test", muid, target1, classOf[EmptyFormula], Seq())
    })

    EventFilter.info(occurrences = 1, source = formula2.path.toString).intercept({
      formula2 ! FormulaStart("test", muid, target2, classOf[EmptyFormula], Seq())
    })

    EventFilter.warning(occurrences = 2, source = formula1.path.toString).intercept({
      formula1 ! FormulaStart("test", muid, target1, classOf[EmptyFormula], Seq())
      formula1 ! FormulaStop("test", UUID.randomUUID())
    })

    EventFilter.info(occurrences = 1, source = formula1.path.toString).intercept({
      formula1 ! FormulaStop("test", muid)
    })

    EventFilter.info(occurrences = 1, source = formula2.path.toString).intercept({
      formula2 ! FormulaStopAll("test")
    })

    Await.result(gracefulStop(formula1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(formula2, timeout.duration), timeout.duration)
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
    val sensorReport1 = MockChannel.SensorReportMock("test", muid1, target1, tick1)
    val sensorReport2 = MockChannel.SensorReportMock("test", muid2, target2, tick2)

    val formula1 = TestActorRef(Props(classOf[EmptyFormula], eventBus, muid1, target1, testActor), "sensor1")
    val formula2 = TestActorRef(Props(classOf[EmptyFormula], eventBus, muid2, target2, testActor), "sensor2")
    val watcher = TestProbe()
    watcher.watch(formula1)
    watcher.watch(formula2)

    EventFilter.info(occurrences = 1, source = formula1.path.toString).intercept({
      formula1 ! FormulaStart("test", muid1, target1, classOf[EmptyFormula], Seq())
    })

    EventFilter.info(occurrences = 1, source = formula2.path.toString).intercept({
      formula2 ! FormulaStart("test", muid2, target2, classOf[EmptyFormula], Seq())
    })

    MockChannel.publishSensorReportMock(muid1, target1, tick1)(eventBus)
    var msg = expectMsgClass(classOf[MockChannel.SensorReportMock])
    msg.muid should equal(sensorReport1.muid)
    msg.target should equal(sensorReport1.target)
    msg.tick should equal(sensorReport1.tick)
    expectNoMsg()

    EventFilter.info(occurrences = 1, source = formula1.path.toString).intercept({
      formula1 ! FormulaStop("test", muid1)
    })

    MockChannel.publishSensorReportMock(muid2, target2, tick2)(eventBus)
    msg = expectMsgClass(classOf[MockChannel.SensorReportMock])
    msg.muid should equal(sensorReport2.muid)
    msg.target should equal(sensorReport2.target)
    msg.tick should equal(sensorReport2.tick)

    EventFilter.info(occurrences = 1, source = formula2.path.toString).intercept({
      formula2 ! FormulaStopAll("test")
    })

    MockChannel.publishSensorReportMock(muid1, target1, tick3)(eventBus)
    MockChannel.publishSensorReportMock(muid2, target2, tick3)(eventBus)
    expectNoMsg()

    watcher.receiveN(2).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs Seq(formula1, formula2)

    Await.result(gracefulStop(formula1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(formula2, timeout.duration), timeout.duration)
  }

  "A Formulas actor" should "handle Formula actors" in new Bus {
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

    val formulas = TestActorRef(Props(classOf[Formulas], eventBus), "formulas")
    val watcher = TestProbe()

    EventFilter.info(occurrences = 4, start = s"formula is started, class: ${classOf[EmptyFormula].getName}").intercept({
      startFormula(muid1, target1, classOf[EmptyFormula], Seq(eventBus, muid1, target1, testActor))(eventBus)
      startFormula(muid1, target2, classOf[EmptyFormula], Seq(eventBus, muid1, target2, testActor))(eventBus)
      startFormula(muid2, target3, classOf[EmptyFormula], Seq(eventBus, muid2, target3, testActor))(eventBus)
      startFormula(muid2, target4, classOf[EmptyFormula], Seq(eventBus, muid2, target4, testActor))(eventBus)
    })

    EventFilter.warning(occurrences = 1, start = s"this formula is started already, class: ${classOf[EmptyFormula].getName}").intercept({
      startFormula(muid1, target1, classOf[EmptyFormula], Seq(eventBus, muid1, target1, testActor))(eventBus)
    })

    val formulaChildren = formulas.children.seq
    formulaChildren foreach watcher.watch

    MockChannel.publishSensorReportMock(muid1, target1, tick1)(eventBus)
    var msg = expectMsgClass(classOf[MockChannel.SensorReportMock])
    msg.muid should equal(muid1)
    msg.target should equal(target1)
    msg.tick should equal(tick1)

    MockChannel.publishSensorReportMock(muid1, target2, tick1)(eventBus)
    msg = expectMsgClass(classOf[MockChannel.SensorReportMock])
    msg.muid should equal(muid1)
    msg.target should equal(target2)
    msg.tick should equal(tick1)

    MockChannel.publishSensorReportMock(muid2, target3, tick2)(eventBus)
    msg = expectMsgClass(classOf[MockChannel.SensorReportMock])
    msg.muid should equal(muid2)
    msg.target should equal(target3)
    msg.tick should equal(tick2)

    stopFormula(muid1)(eventBus)
    stopAllFormula(eventBus)

    watcher.receiveN(4).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs formulaChildren

    Await.result(gracefulStop(formulas, timeout.duration), timeout.duration)
  }
}
