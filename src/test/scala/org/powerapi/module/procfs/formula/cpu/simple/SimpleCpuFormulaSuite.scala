/**
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

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.module.procfs.formula.cpu.simple

import java.util.UUID
import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import scala.concurrent.duration.DurationInt

trait SimpleCpuFormulaConfigurationMock extends Configuration {
  override lazy val tdp = 220
  override lazy val tdpFactor = 0.7
}

class SimpleCpuFormulaMock(messageBus: MessageBus)
  extends CpuFormula(messageBus)
  with SimpleCpuFormulaConfigurationMock

class MockSubscriber(eventBus: MessageBus, muid: UUID, actorRef: ActorRef) extends Actor {
  import org.powerapi.formula.FormulaChannel.{PowerReport, subscribePowerReport}

  override def preStart() = {
    subscribePowerReport(muid)(eventBus)(self)
  }

  def receive = {
    case msg: PowerReport => actorRef ! msg
  }
}

class SimpleCpuFormulaSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SimpleCpuFormulaSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus
  val formulaMock = TestActorRef(Props(classOf[SimpleCpuFormulaMock], eventBus), "simple-cpuFormula")(system)

  "A simple cpu formula" should "process a SensorReport and then publish a PowerReport" in {
    import org.powerapi.core.Process
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.formula.FormulaChannel.PowerReport
    import org.powerapi.formula.PowerUnit
    import org.powerapi.module.procfs.sensor.cpu.CpuProcfsSensorChannel.{CpuProcfsSensorReport, TargetRatio, TimeInStates}

    val topic = "test"
    val muid = UUID.randomUUID()
    val target = Process(1)
    val targetRatio = TargetRatio(0.4)
    val tick = ClockTick("clock", 25.milliseconds)
    val power = 220 * 0.7 * targetRatio.percent

    TestActorRef(Props(classOf[MockSubscriber], eventBus, muid, testActor), "subscriber")(system)

    formulaMock.underlyingActor.asInstanceOf[CpuFormula].compute(CpuProcfsSensorReport(topic, muid, target, targetRatio, TimeInStates(Map()), tick))

    expectMsgClass(classOf[PowerReport]) match {
      case PowerReport(_, id, targ, pow, PowerUnit.W, tic) if muid == id && target == targ && power == pow && tick == tic => assert(true)
      case _ => assert(false)
    }
  }
}
