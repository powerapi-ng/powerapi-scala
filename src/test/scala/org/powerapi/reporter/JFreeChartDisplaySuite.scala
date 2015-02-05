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

import scala.concurrent.duration._

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ TestActorRef, TestKit }
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MessageBus

class JFreeChartDisplaySuite(system: ActorSystem) extends UnitTest(system) {
  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("JFreeChartDisplaySuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus

  "A JFreeChart reporter" should "process a power report and then report energy information in a chart" ignore {
    import org.powerapi.core.target.intToProcess
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.power._
    import org.powerapi.module.PowerChannel.{ AggregateReport, RawPowerReport, render, subscribeAggPowerReport }
    
    val reporterMock = TestActorRef(Props(classOf[ReporterComponent], new JFreeChartDisplay), "jfreechartReporter")(system)
    
    val muid = UUID.randomUUID()
    val aggFunction = (s: Seq[Power]) => s.foldLeft(0.0.W){ (acc, p) => acc + p }
  
    subscribeAggPowerReport(muid)(eventBus)(reporterMock)

    val begin = System.currentTimeMillis
    var current = begin

    while(current <= (begin + (5.seconds).toMillis)) {
      render(AggregateReport(muid, aggFunction) += RawPowerReport("topictest",
                                                                  muid,
                                                                  1,
                                                                  Math.random.W,
                                                                  "mock",
                                                                  ClockTick("ticktest", 25.milliseconds, current)))(eventBus)
      current += 1.seconds.toMillis
      Thread.sleep(1.seconds.toMillis)
    }
  }
}

