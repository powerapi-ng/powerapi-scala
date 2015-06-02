/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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
package org.powerapi.module.powerspy

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.gracefulStop
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{MessageBus, ExternalPMeter}
import org.powerapi.module.powerspy.PowerSpyChannel.publishExternalPowerSpyPower
import org.powerapi.core.power._
import org.powerapi.module.powerspy.PowerSpyChannel.{PowerSpyPower, subscribePowerSpyPower}
import scala.concurrent.duration.DurationInt

class MockPMeter(eventBus: MessageBus) extends ExternalPMeter {
  def init(): Unit = {}
  def start(): Unit = {
    publishExternalPowerSpyPower(10.W)(eventBus)
    publishExternalPowerSpyPower(20.W)(eventBus)
    publishExternalPowerSpyPower(14.W)(eventBus)
  }
  def stop(): Unit = {}
}

class PowerSpyPowerListener(eventBus: MessageBus) extends Actor {
  override def preStart(): Unit = {
    subscribePowerSpyPower(eventBus)(self)
  }

  def receive = {
    case msg: PowerSpyPower => println(msg)
  }
}

class PowerSpySensorSuite(system: ActorSystem) extends UnitTest(system) {
  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("PowerSpySensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait EventBus {
    val eventBus = new MessageBus
  }

  "A PowerSpySensor" should "listen PowerSpyPower messages, build a new message and then publish it" in new EventBus {
    subscribePowerSpyPower(eventBus)(testActor)
    val pSpySensor = TestActorRef(Props(classOf[PowerSpySensor], eventBus, new MockPMeter(eventBus)), "pSpySensor")(system)

    expectMsgClass(classOf[PowerSpyPower]) match {
      case PowerSpyPower(_, power) => power should equal(10.W)
    }
    expectMsgClass(classOf[PowerSpyPower]) match {
      case PowerSpyPower(_, power) => power should equal(20.W)
    }
    expectMsgClass(classOf[PowerSpyPower]) match {
      case PowerSpyPower(_, power) => power should equal(14.W)
    }

    gracefulStop(pSpySensor, 15.seconds)
  }

  it should "open the connection with the power meter, build new messages and then publish them" ignore new EventBus {
    val listener = TestActorRef(Props(classOf[PowerSpyPowerListener], eventBus), "listener")(system)
    val pSpySensor = TestActorRef(Props(classOf[PowerSpySensor], eventBus, new PowerSpyPMeter(eventBus, "00:0b:ce:07:1e:9b", 1.seconds)), "pSpySensor")(system)

    Thread.sleep(15.seconds.toMillis)

    gracefulStop(pSpySensor, 15.seconds)
    gracefulStop(listener, 15.seconds)
  }
}
