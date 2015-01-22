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

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import org.powerapi.UnitTest
import org.powerapi.module.PowerUnit

class PowerSpyFormulaSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("PowerSpyFormulaSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A PowerSpyFormula" should "listen PowerSpyPower/UsageReport messages and produce PowerReport messages" in {
    import java.util.UUID
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MessageBus
    import org.powerapi.core.target.{intToProcess, Process, TargetUsageRatio}
    import org.powerapi.module.PowerChannel.{PowerReport, subscribePowerReport}
    import org.powerapi.module.cpu.UsageMetricsChannel.publishUsageReport
    import org.powerapi.module.powerspy.PowerSpyChannel.publishSensorPower
    import scala.concurrent.duration.DurationInt

    val eventBus = new MessageBus
    TestActorRef(Props(classOf[PowerSpyFormula], eventBus))(system)
    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)
    subscribePowerReport(muid)(eventBus)(testActor)

    publishUsageReport(muid, 1, TargetUsageRatio(0.5), tickMock)(eventBus)
    expectNoMsg(3.seconds)

    publishSensorPower(90.0, PowerUnit.W)(eventBus)
    publishSensorPower(92.0, PowerUnit.W)(eventBus)
    publishSensorPower(150.0, PowerUnit.W)(eventBus)
    publishUsageReport(muid, 1, TargetUsageRatio(0.5), tickMock)(eventBus)
    expectMsgClass(classOf[PowerReport]) match {
      case PowerReport(_, id, targ, pow, PowerUnit.W, "powerspy", tic) => id should equal(muid); targ should equal(Process(1)); pow should equal(150 * 0.5); tic should equal(tickMock)
    }

    publishSensorPower(140.0, PowerUnit.W)(eventBus)
    publishUsageReport(muid, 1, TargetUsageRatio(0.25), tickMock)(eventBus)
    expectMsgClass(classOf[PowerReport]) match {
      case PowerReport(_, id, targ, pow, PowerUnit.W, "powerspy", tic) => id should equal(muid); targ should equal(Process(1)); pow should equal(140 * 0.25); tic should equal(tickMock)
    }
    publishUsageReport(muid, 2, TargetUsageRatio(0.25), tickMock)(eventBus)
    expectMsgClass(classOf[PowerReport]) match {
      case PowerReport(_, id, targ, pow, PowerUnit.W, "powerspy", tic) => id should equal(muid); targ should equal(Process(2)); pow should equal(140 * 0.25); tic should equal(tickMock)
    }
  }
}
