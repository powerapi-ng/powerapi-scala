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
package org.powerapi.module.extPMeter

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.gracefulStop
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{MessageBus, ExternalPMeter}
import org.powerapi.module.extPMeter.ExtPMeterChannel.publishExternalPMeterPower
import org.powerapi.core.power._
import org.powerapi.module.extPMeter.ExtPMeterChannel.{ExtPMeterPower, subscribePMeterPower}
import org.powerapi.module.extPMeter.powerspy.PowerSpyPMeter
import org.powerapi.module.extPMeter.g5k.G5kPMeter
import scala.concurrent.duration.DurationInt

class MockPMeter(eventBus: MessageBus) extends ExternalPMeter {
  def init(bus: MessageBus): Unit = {}
  def start(): Unit = {
    publishExternalPMeterPower(10.W)(eventBus)
    publishExternalPMeterPower(20.W)(eventBus)
    publishExternalPMeterPower(14.W)(eventBus)
  }
  def stop(): Unit = {}
}

class ExtPMeterPowerListener(eventBus: MessageBus) extends Actor {
  override def preStart(): Unit = {
    subscribePMeterPower(eventBus)(self)
  }

  def receive = {
    case msg: ExtPMeterPower => println(msg)
  }
}

class ExtPMeterSensorSuite(system: ActorSystem) extends UnitTest(system) {
  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("ExtPMeterSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait EventBus {
    val eventBus = new MessageBus
  }

  "A ExtPMeterSensor" should "listen ExtPMeterPower messages, build a new message and then publish it" in new EventBus {
    subscribePMeterPower(eventBus)(testActor)
    val extpmSensor = TestActorRef(Props(classOf[ExtPMeterSensor], eventBus, new MockPMeter(eventBus)), "extpmSensor")(system)

    expectMsgClass(classOf[ExtPMeterPower]) match {
      case ExtPMeterPower(_, power) => power should equal(10.W)
    }
    expectMsgClass(classOf[ExtPMeterPower]) match {
      case ExtPMeterPower(_, power) => power should equal(20.W)
    }
    expectMsgClass(classOf[ExtPMeterPower]) match {
      case ExtPMeterPower(_, power) => power should equal(14.W)
    }

    gracefulStop(extpmSensor, 15.seconds)
  }

  it should "open the connection with the PowerSpy, build new messages and then publish them" ignore new EventBus {
    val listener = TestActorRef(Props(classOf[ExtPMeterPowerListener], eventBus), "listener")(system)
    val extpmSensor = TestActorRef(Props(classOf[ExtPMeterSensor], eventBus, new PowerSpyPMeter("00:06:66:4D:F4:BB", 1.seconds)), "pSpySensor")(system)

    Thread.sleep(15.seconds.toMillis)

    gracefulStop(extpmSensor, 15.seconds)
    gracefulStop(listener, 15.seconds)
  }
  
  it should "open the connection with the grid5000 OmegaWatt, build new messages and then publish them" ignore new EventBus {
    val listener = TestActorRef(Props(classOf[ExtPMeterPowerListener], eventBus), "listener")(system)
    val extpmSensor = TestActorRef(Props(classOf[ExtPMeterSensor], eventBus, new G5kPMeter("http://kwapi.lyon.grid5000.fr:5000/probes/lyon.taurus-1/power/", 1.seconds)), "g5kowSensor")(system)

    Thread.sleep(15.seconds.toMillis)

    gracefulStop(extpmSensor, 15.seconds)
    gracefulStop(listener, 15.seconds)
  }
}
