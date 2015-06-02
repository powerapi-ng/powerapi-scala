/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ TestActorRef, TestKit }
import akka.util.Timeout
import java.util.UUID
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.core.power.Power
import org.powerapi.core.target.{intToProcess, Target}
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.power._
import org.powerapi.module.PowerChannel.{ AggregatePowerReport, RawPowerReport, render, subscribeAggPowerReport }
import scala.concurrent.duration.DurationInt

class ConsoleDisplayMock(testActor: ActorRef) extends ConsoleDisplay {
  override def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power) {
    testActor ! s"muid=$muid;timestamp=$timestamp;targets=${targets.mkString(",")};devices=${devices.mkString(",")};power=${power.toWatts}"
  }
}

class ConsoleDisplaySuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("ConsoleDisplaySuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus
  val reporterMock = TestActorRef(Props(classOf[ReporterComponent], new ConsoleDisplayMock(testActor)), "consoleReporter")(system)
  
  "A console reporter" should "process a PowerReport and then report energy information in a String format" in {
    val muid = UUID.randomUUID()
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
    val aggFunction = (s: Seq[Power]) => s.foldLeft(0.0.W){ (acc, p) => acc + p }
  
    subscribeAggPowerReport(muid)(eventBus)(reporterMock)
    
    val aggR1 = AggregatePowerReport(muid, aggFunction)
    aggR1 += RawPowerReport("topictest", muid, 1, 3.W, device, tickMock)
    
    val aggR2 = AggregatePowerReport(muid, aggFunction)
    aggR2 += RawPowerReport("topictest", muid, 2, 1.W, device, tickMock)
    
    val aggR3 = AggregatePowerReport(muid, aggFunction)
    aggR3 += RawPowerReport("topictest", muid, 3, 2.W, device, tickMock)
    aggR3 += RawPowerReport("topictest", muid, 4, 4.W, device, tickMock)
    
    render(aggR1)(eventBus)
    render(aggR2)(eventBus)
    render(aggR3)(eventBus)
    
    expectMsgClass(classOf[String]) should equal(s"muid=$muid;timestamp=${tickMock.timestamp};targets=1;devices=$device;power=${3.W.toWatts}")
    expectMsgClass(classOf[String]) should equal(s"muid=$muid;timestamp=${tickMock.timestamp};targets=2;devices=$device;power=${1.W.toWatts}")
    expectMsgClass(classOf[String]) should equal(s"muid=$muid;timestamp=${tickMock.timestamp};targets=3,4;devices=$device;power=${6.W.toWatts}")
  }
}

