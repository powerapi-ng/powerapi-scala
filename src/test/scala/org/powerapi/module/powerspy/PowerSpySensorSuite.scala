/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2014 Inria, University of Lille 1.
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

import akka.actor.{Actor, Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import scala.concurrent.duration.DurationInt

class PowerSpySensorMock(eventBus: MessageBus, timeout: Timeout) extends PowerSpySensor(eventBus, timeout) {
  override lazy val sppUrl = "btspp://000BCE071E9B:1;authenticate=false;encrypt=false;master=false"
  override lazy val version = PowerSpyVersion.POWERSPY_V1
}

class OverallPowerListener(eventBus: MessageBus) extends Actor {
  import org.powerapi.module.OverallPowerChannel.{OverallPower, subscribeOverallPower}

  override def preStart(): Unit = {
    subscribeOverallPower(eventBus)(self)
  }

  def receive = {
    case msg: OverallPower => println(msg)
  }
}

class PowerSpySensorSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("PowerSpySensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A PowerSpySensor" should "open the connection with the power meter, compute the power and publish OverallPowerReports at its own frequency" ignore {
    import akka.pattern.gracefulStop
    import org.powerapi.module.PowerUnit
    import org.powerapi.module.OverallPowerChannel.{OverallPower, subscribeOverallPower}

    val eventBus = new MessageBus
    val pspySensor = TestActorRef(Props(classOf[PowerSpySensorMock], eventBus, Timeout(15.seconds)), "pSpySensor")(system)
    subscribeOverallPower(eventBus)(testActor)

    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].receive(PSpyData(3.0, 2.0f, 3.0f))
    expectMsgClass(classOf[OverallPower]) match {
      case OverallPower(_, power, PowerUnit.W, "powerspy") => power should equal(3.0 * 2.0f * 3.0f)
    }

    TestActorRef(Props(classOf[OverallPowerListener], eventBus), "listener")(system)
    Thread.sleep(20.seconds.toMillis)
    gracefulStop(pspySensor, 15.seconds)
  }
}
