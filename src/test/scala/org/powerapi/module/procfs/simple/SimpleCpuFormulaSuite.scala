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
package org.powerapi.module.procfs.simple

import java.util.UUID
import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.module.PowerChannel
import org.powerapi.module.procfs.CpuProcfsSensorChannel
import scala.concurrent.duration.DurationInt

trait SimpleCpuFormulaConfigurationMock extends FormulaConfiguration {
  override lazy val tdp = 220
  override lazy val tdpFactor = 0.7
}

class SimpleCpuFormulaMock(messageBus: MessageBus)
  extends CpuFormula(messageBus)
  with SimpleCpuFormulaConfigurationMock

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
    import PowerChannel.{PowerReport, subscribePowerReport}
    import CpuProcfsSensorChannel.{publishCpuProcfsReport, TargetRatio}
    import org.powerapi.module.PowerUnit

    val muid = UUID.randomUUID()
    val target = Process(1)
    val targetRatio = TargetRatio(0.4)
    val tickMock = ClockTick("test", 25.milliseconds)
    val power = 220 * 0.7 * targetRatio.percent

    subscribePowerReport(muid)(eventBus)(testActor)
    publishCpuProcfsReport(muid, target, targetRatio, tickMock)(eventBus)

    expectMsgClass(classOf[PowerReport]) match {
      case PowerReport(_, id, targ, pow, PowerUnit.W, "cpu", tic) if muid == id && target == targ && power == pow && tickMock == tic => assert(true)
      case _ => assert(false)
    }
  }
}
