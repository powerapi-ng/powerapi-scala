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

class ReporterComponentMock(actorRef: ActorRef) extends ReporterComponent {
  def report(aggPowerReport: PowerReport): Unit = {
    actorRef ! aggPowerReport
  }
}

class ReporterComponentSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("ReporterComponentSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A reporter component" should "process PowerReport messages" in new Bus {
    import org.powerapi.core.Process
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.module.PowerChannel.{ AggregateReport, initAggPowerReport, render, subscribeAggPowerReport }
    import org.powerapi.module.PowerUnit
    
    val reporterMock = TestActorRef(Props(classOf[ReporterComponentMock], testActor))(system)
    
    val muid = UUID.randomUUID()
    val target = Process(1)
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
    val aggFunction = (l: List[PowerReport]) => Some(PowerReport("Sum",
                                                      l.last.muid,
                                                      l.last.target,
                                                      l.foldLeft(0.0){ (acc, r) => acc + r.power },
                                                      l.last.unit,
                                                      l.last.device,
                                                      l.last.tick))
  
    subscribeAggPowerReport(muid)(eventBus)(reporterMock)
    
    val aggP = initAggPowerReport(muid, aggFunction)
    aggP.asInstanceOf[AggregateReport[PowerReport]] += PowerReport("topictest", muid, target, 1.0, PowerUnit.W, device, tickMock)
    aggP.asInstanceOf[AggregateReport[PowerReport]] += PowerReport("topictest", muid, target, 2.0, PowerUnit.W, device, tickMock)
    aggP.asInstanceOf[AggregateReport[PowerReport]] += PowerReport("topictest", muid, target, 3.0, PowerUnit.W, device, tickMock)
    
    render(aggP)(eventBus)
    
    expectMsgClass(classOf[PowerReport]) match {
      case msg: PowerReport => msg.muid should equal(muid)
                               msg.asInstanceOf[AggregateReport[PowerReport]].agg match {
                                case Some(aggPowerReport) => aggPowerReport.power should equal(6.0)
                                case None => false
                               }
    }
  }
}

