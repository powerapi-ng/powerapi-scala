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
package org.powerapi

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.actor.{Actor, Props, Terminated}
import akka.pattern.{ask, gracefulStop}
import akka.testkit.{TestActorRef, TestProbe}
import akka.util.Timeout
import org.powerapi.core.power.Power
import org.powerapi.core.target.{Application, Process, Target}
import org.powerapi.core.{MessageBus, Monitor}
import org.powerapi.module.PowerChannel.AggregatePowerReport
import org.powerapi.module.{Formula, Sensor}

class EmptySensor(eventBus: MessageBus, muid: UUID, target: Target) extends Sensor(eventBus, muid, target) {
  def init(): Unit = {}

  def terminate(): Unit = {}

  def handler: Actor.Receive = sensorDefault
}

class EmptyFormula(eventBus: MessageBus, muid: UUID, target: Target) extends Formula(eventBus, muid, target) {
  def init(): Unit = {}

  def terminate(): Unit = {}

  def handler: Actor.Receive = formulaDefault
}

object EmptyModule extends PowerModule {
  val sensor = Some((classOf[EmptySensor], Seq[Any]()))
  val formula = Some((classOf[EmptyFormula], Seq[Any]()))
}

class PowerMeterSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus()
  }

  "The PowerMeterConfiguration" should "be correctly read from a resource file" in {
    val configuration = new PowerMeterConfiguration()
    configuration.timeout should equal(Timeout(10.seconds))
  }

  "A PowerMeterActor" should "be able to handle a software-defined power meter" in new Bus {
    val actor = TestActorRef(Props(classOf[PowerMeterActor], eventBus, Seq(EmptyModule)))
    val out = new PowerDisplay {
      def display(aggregatePowerReport: AggregatePowerReport): Unit = {}
    }

    val watcher = TestProbe()
    val monitor = Await.result(actor.ask(MonitorMessage(Set(Process(1), Application("java"))))(timeout), timeout.duration).asInstanceOf[Monitor]
    monitor.to(out)
    monitor.every(1.second)

    watcher.watch(actor.actorRef)
    val actorChildren = actor.children.seq
    actorChildren foreach watcher.watch

    Await.result(actor.ask(WaitForMessage(3.seconds))(10.seconds), 10.seconds).asInstanceOf[String] should equal("waitFor completed")

    actor ! ShutdownMessage

    watcher.receiveN(6).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs Seq(actor.actorRef) ++ actorChildren
    Await.result(gracefulStop(actor, timeout.duration), timeout.duration)
  }

  "A PowerMeter" should "act like a mirror to a PowerMeterActor" in {
    val pMeter = new PowerMeter(system, Seq(EmptyModule))

    pMeter.monitor(1, "java")

    within(10.seconds) {
      pMeter.waitFor(3.seconds)
    }

    pMeter.shutdown()
  }
}
