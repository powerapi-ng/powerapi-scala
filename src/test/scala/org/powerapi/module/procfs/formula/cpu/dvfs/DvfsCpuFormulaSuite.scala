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

package org.powerapi.module.procfs.formula.cpu.dvfs

import java.util.UUID

import akka.actor.{Props, Actor, ActorRef, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.module.procfs.formula.cpu.simple.CpuFormula
import scala.concurrent.duration.DurationInt

trait DvfsCpuFormulaConfigurationMock extends Configuration {
  override lazy val tdp = 220
  override lazy val tdpFactor = 0.7
}

class DvfsCpuFormulaMock(messageBus: MessageBus)
  extends CpuFormula(messageBus)
  with DvfsCpuFormulaConfigurationMock

class MockSubscriber(eventBus: MessageBus, muid: UUID, actorRef: ActorRef) extends Actor {
  import org.powerapi.formula.FormulaChannel.{PowerReport, subscribePowerReport}

  override def preStart() = {
    subscribePowerReport(muid)(eventBus)(self)
  }

  def receive = {
    case msg: PowerReport => actorRef ! msg
  }
}

class DvfsCpuFormulaSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("DvfsCpuFormulaSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus
  val formulaMock = TestActorRef(Props(classOf[DvfsCpuFormulaMock], eventBus), "dvfs-cpuFormula")(system)
  val frequencies = Map(1800002 -> 1.31, 2100002 -> 1.41, 2400003 -> 1.5)

  "A dvfs cpu formula" should "read frequencies in a configuration file" in {
    formulaMock.underlyingActor.asInstanceOf[CpuFormula].frequencies should equal(frequencies)
  }

  it should "compute correctly the constant" in {
    formulaMock.underlyingActor.asInstanceOf[CpuFormula].constant should equal((220 * 0.7) / (2400003 * math.pow(1.5, 2)))
  }

  it should "compute powers for each frequency" in {
    formulaMock.underlyingActor.asInstanceOf[CpuFormula].powers should equal(Map(
      1800002 -> formulaMock.underlyingActor.asInstanceOf[CpuFormula].constant * 1800002 * math.pow(1.31, 2),
      2100002 -> formulaMock.underlyingActor.asInstanceOf[CpuFormula].constant * 2100002 * math.pow(1.41, 2),
      2400003 -> formulaMock.underlyingActor.asInstanceOf[CpuFormula].constant * 2400003 * math.pow(1.5, 2)
    ))
  }

  it should "compute correctly the process' power" in {
    import org.powerapi.core.Process
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.module.procfs.sensor.cpu.CpuProcfsSensorChannel.{CpuProcfsSensorReport, TargetRatio, TimeInStates}

    val topic = "test"
    val muid = UUID.randomUUID()
    val target = Process(1)
    val targetRatio = TargetRatio(0.5)
    val timeInStates = TimeInStates(Map(1800002 -> 1, 2100002 -> 2, 2400003 -> 3))
    val tick = ClockTick("clock", 25.milliseconds)

    val sensorReport = CpuProcfsSensorReport(topic, muid, target, targetRatio, timeInStates, tick)

    formulaMock.underlyingActor.asInstanceOf[CpuFormula].power(sensorReport) should equal(
      Some(
        (
          formulaMock.underlyingActor.asInstanceOf[CpuFormula].powers(1800002) * 1 +
          formulaMock.underlyingActor.asInstanceOf[CpuFormula].powers(2100002) * 2 +
          formulaMock.underlyingActor.asInstanceOf[CpuFormula].powers(2400003) * 3
        ) / (1 + 2 + 3)
      )
    )
  }

  it should "process a SensorReport and then publish a PowerReport" in {
    import org.powerapi.core.Process
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.formula.FormulaChannel.PowerReport
    import org.powerapi.formula.PowerUnit
    import org.powerapi.module.procfs.sensor.cpu.CpuProcfsSensorChannel.{CpuProcfsSensorReport, TargetRatio, TimeInStates}

    val topic = "test"
    val muid = UUID.randomUUID()
    val target = Process(1)
    val targetRatio = TargetRatio(0.5)
    val timeInStates = TimeInStates(Map(1800002 -> 1, 2100002 -> 2, 2400003 -> 3))
    val tick = ClockTick("clock", 25.milliseconds)
    val power = (
      formulaMock.underlyingActor.asInstanceOf[CpuFormula].powers(1800002) * 1 +
      formulaMock.underlyingActor.asInstanceOf[CpuFormula].powers(2100002) * 2 +
      formulaMock.underlyingActor.asInstanceOf[CpuFormula].powers(2400003) * 3
    ) / (1 + 2 + 3)

    val sensorReport = CpuProcfsSensorReport(topic, muid, target, targetRatio, timeInStates, tick)

    TestActorRef(Props(classOf[MockSubscriber], eventBus, muid, testActor), "subscriber")(system)

    formulaMock.underlyingActor.asInstanceOf[CpuFormula].compute(sensorReport)

    expectMsgClass(classOf[PowerReport]) match {
      case PowerReport(_, id, targ, pow, PowerUnit.W, "cpu", tic) if muid == id && target == targ && power == pow && tick == tic => assert(true)
      case _ => assert(false)
    }
  }
}