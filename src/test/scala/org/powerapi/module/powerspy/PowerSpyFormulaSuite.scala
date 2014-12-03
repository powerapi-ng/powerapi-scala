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

import java.util.UUID

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import scala.concurrent.duration.DurationInt

class PowerSpyFormulaMock(eventBus: MessageBus) extends PowerSpyFormula(eventBus) {
  override lazy val idlePower = 87.50
}

class PowerSpyFormulaSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("PowerSpyFormulaSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus
  val formulaMock = TestActorRef(Props(classOf[PowerSpyFormulaMock], eventBus), "powerspyFormula")(system)

  "A PowerSpyFormula" should "compute the power when receiving a PspyDataReport" in {
    import org.powerapi.core.{All, Process, TargetUsageRatio}
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.module.PowerChannel.{PowerReport, subscribePowerReport}
    import org.powerapi.module.PowerUnit
    import PSpyMetricsChannel.publishPSpyDataReport

    val muid = UUID.randomUUID()
    val rms = 2000000d
    val u = 0.080f
    val i = 5.7E-4f
    val ratio = TargetUsageRatio(0.80)
    val allPower = rms * u * i
    val processPower = (rms * u * i - 87.50) * ratio.ratio
    val tickMock = ClockTick("test", 1.seconds)

    subscribePowerReport(muid)(eventBus)(testActor)

    publishPSpyDataReport(muid, All, rms, u, i, tickMock)(eventBus)
    expectMsgClass(classOf[PowerReport]) match {
      case PowerReport(_, id, All, pow, PowerUnit.W, "powerspy", tic) => id should equal(muid); pow should equal(allPower); tic should equal(tickMock)
    }

    publishPSpyDataReport(muid, Process(1), ratio, rms, u, i, tickMock)(eventBus)
    expectMsgClass(classOf[PowerReport]) match {
      case PowerReport(_, id, pr, pow, PowerUnit.W, "powerspy", tic) => id should equal(muid); pr should equal(Process(1)); tic should equal(tickMock)
    }
  }
}
