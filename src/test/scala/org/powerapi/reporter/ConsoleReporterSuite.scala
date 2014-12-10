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
package org.powerapi.reporter

import java.util.UUID

import scala.concurrent.duration.DurationInt

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ TestActorRef, TestKit }
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.reporter.AggPowerChannel.AggPowerReport

class ConsoleReporterMock(testActor: ActorRef) extends ConsoleReporter {
  override def report(aggPowerReport: AggPowerReport) = {
    testActor ! Line(aggPowerReport).toString
  }
}

case class LineMock(aggPowerReport: AggPowerReport) {
  override def toString() =
    "timestamp=" + aggPowerReport.tick.timestamp + ";" +
    "target=" + aggPowerReport.target + ";" +
    "device=" + aggPowerReport.device + ";" +
    "value=" + aggPowerReport.power + aggPowerReport.unit
}

class ConsoleReporterSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("ConsoleReporterSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus
  val reporterMock = TestActorRef(Props(classOf[ConsoleReporterMock], testActor), "consoleReporter")(system)

  "A console reporter" should "process a PowerReport and then report energy information in a String format" in {
    import AggPowerChannel.{ render, subscribeAggPowerReport }
    import org.powerapi.core.Process
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.module.PowerChannel.PowerReport
    import org.powerapi.module.PowerUnit
    
    val muid = UUID.randomUUID()
    val target = Process(1)
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
  
    subscribeAggPowerReport(muid)(eventBus)(reporterMock)
    render(PowerReport("topictest", muid, target, 1.0, PowerUnit.W, device, tickMock))(eventBus)
    render(PowerReport("topictest", muid, target, 2.0, PowerUnit.W, device, tickMock))(eventBus)
    render(PowerReport("topictest", muid, target, 3.0, PowerUnit.W, device, tickMock))(eventBus)
    
    expectMsgClass(classOf[String]) should equal(LineMock(AggPowerReport("topictest", muid, target, 1.0, PowerUnit.W, device, tickMock)).toString)
    expectMsgClass(classOf[String]) should equal(LineMock(AggPowerReport("topictest", muid, target, 2.0, PowerUnit.W, device, tickMock)).toString)
    expectMsgClass(classOf[String]) should equal(LineMock(AggPowerReport("topictest", muid, target, 3.0, PowerUnit.W, device, tickMock)).toString)
  }
}

