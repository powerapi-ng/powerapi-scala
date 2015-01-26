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
import org.powerapi.module.PowerChannel.PowerReport

class ConsoleReporterMock(testActor: ActorRef) extends ConsoleReporter {
  override def report(aggPowerReport: PowerReport) = {
    testActor ! aggPowerReport.toString
  }
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
    import org.powerapi.core.Process
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.power._
    import org.powerapi.module.PowerChannel.{ AggregateReport, RawPowerReport, render, subscribeAggPowerReport }
    
    val muid = UUID.randomUUID()
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
    val aggFunction = (s: Seq[Power]) => s.foldLeft(0.0.W){ (acc, p) => acc + p }
  
    subscribeAggPowerReport(muid)(eventBus)(reporterMock)
    
    val aggR1 = AggregateReport(muid, aggFunction)
    aggR1 += RawPowerReport("topictest", muid, Process(1), 3.W, device, tickMock)
    
    val aggR2 = AggregateReport(muid, aggFunction)
    aggR2 += RawPowerReport("topictest", muid, Process(2), 1.W, device, tickMock)
    
    val aggR3 = AggregateReport(muid, aggFunction)
    aggR3 += RawPowerReport("topictest", muid, Process(3), 2.W, device, tickMock)
    
    render(aggR1)(eventBus)
    render(aggR2)(eventBus)
    render(aggR3)(eventBus)
    
    expectMsgClass(classOf[String]) should equal(RawPowerReport("topictest", muid, Process(1), 3.W, device, tickMock).toString)
    expectMsgClass(classOf[String]) should equal(RawPowerReport("topictest", muid, Process(2), 1.W, device, tickMock).toString)
    expectMsgClass(classOf[String]) should equal(RawPowerReport("topictest", muid, Process(3), 2.W, device, tickMock).toString)
  }
}

