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
package org.powerapi.module.cpu.dvfs

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import java.util.UUID
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
import org.powerapi.core.TimeInStates
import org.powerapi.core.target.{intToProcess, Target, TargetUsageRatio}
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.power._
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.cpu.UsageMetricsChannel.publishUsageReport
import scala.concurrent.duration.DurationInt

class DvfsCpuFormulaSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("DvfsCpuFormulaSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus
  val frequencies = Map(1800002 -> 1.31, 2100002 -> 1.41, 2400003 -> 1.5)
  val formula = TestActorRef(Props(classOf[CpuFormula], eventBus, 220.0, 0.7, frequencies), "dvfs-cpuFormula")(system)

  it should "compute correctly the constant" in {
    formula.underlyingActor.asInstanceOf[CpuFormula].constant should equal((220 * 0.7) / (2400003 * math.pow(1.5, 2)))
  }

  it should "compute powers for each frequency" in {
    formula.underlyingActor.asInstanceOf[CpuFormula].powers should equal(Map(
      1800002 -> formula.underlyingActor.asInstanceOf[CpuFormula].constant * 1800002 * math.pow(1.31, 2),
      2100002 -> formula.underlyingActor.asInstanceOf[CpuFormula].constant * 2100002 * math.pow(1.41, 2),
      2400003 -> formula.underlyingActor.asInstanceOf[CpuFormula].constant * 2400003 * math.pow(1.5, 2)
    ))
  }

  it should "compute correctly the process' power" in {
    val topic = "test"
    val muid = UUID.randomUUID()
    val target: Target = 1
    val targetRatio = TargetUsageRatio(0.5)
    val timeInStates = TimeInStates(Map(1800002l -> 1l, 2100002l -> 2l, 2400003l -> 3l))
    val tick = ClockTick("clock", 25.milliseconds)

    val sensorReport = UsageReport(topic, muid, target, targetRatio, timeInStates, tick)

    formula.underlyingActor.asInstanceOf[CpuFormula].power(sensorReport) should equal(
      Some(
        (
          formula.underlyingActor.asInstanceOf[CpuFormula].powers(1800002) * 1 +
          formula.underlyingActor.asInstanceOf[CpuFormula].powers(2100002) * 2 +
          formula.underlyingActor.asInstanceOf[CpuFormula].powers(2400003) * 3
        ) / (1 + 2 + 3)
      )
    )
  }

  it should "process a SensorReport and then publish a PowerReport" in {
    val muid = UUID.randomUUID()
    val target: Target = 1
    val targetRatio = TargetUsageRatio(0.5)
    val timeInStates = TimeInStates(Map(1800002l -> 1l, 2100002l -> 2l, 2400003l -> 3l))
    val tickMock = ClockTick("test", 25.milliseconds)
    val power = ((
      formula.underlyingActor.asInstanceOf[CpuFormula].powers(1800002) * 1 +
      formula.underlyingActor.asInstanceOf[CpuFormula].powers(2100002) * 2 +
      formula.underlyingActor.asInstanceOf[CpuFormula].powers(2400003) * 3
    ) / (1 + 2 + 3)).W

    subscribeRawPowerReport(muid)(eventBus)(testActor)
    publishUsageReport(muid, target, targetRatio, timeInStates, tickMock)(eventBus)

    val ret = expectMsgClass(classOf[RawPowerReport])
    ret.muid should equal(muid)
    ret.target should equal(target)
    ret.power should equal(power)
    ret.device should equal("cpu")
    ret.tick should equal(tickMock)
  }
}
