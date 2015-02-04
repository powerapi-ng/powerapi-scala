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

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ TestActorRef, TestKit }
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.module.PowerChannel.PowerReport

object ConfigurationMock {
  val testPath = "powerapi-reporter-file-test"
}

trait ConfigurationMock extends Configuration {
  override lazy val filePath = ConfigurationMock.testPath
}

class FileDisplayMock extends FileDisplay with ConfigurationMock

class FileDisplaySuite(system: ActorSystem) extends UnitTest(system) {
  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("FileDisplaySuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus
  val reporterMock = TestActorRef(Props(classOf[ReporterComponent], new FileDisplayMock), "fileReporter")(system)

  "A file reporter" should "process a power report and then report energy information in a file" in {
    import scalax.file.Path
    import org.powerapi.core.target.intToProcess
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.power._
    import org.powerapi.module.PowerChannel.{ AggregateReport, RawPowerReport, render, subscribeAggPowerReport }
    
    val muid = UUID.randomUUID()
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
    val aggFunction = (s: Seq[Power]) => s.foldLeft(0.0.W){ (acc, p) => acc + p }
  
    subscribeAggPowerReport(muid)(eventBus)(reporterMock)
    
    val aggR1 = AggregateReport(muid, aggFunction)
    aggR1 += RawPowerReport("topictest", muid, 1, 3.W, device, tickMock)
    
    val aggR2 = AggregateReport(muid, aggFunction)
    aggR2 += RawPowerReport("topictest", muid, 2, 1.W, device, tickMock)
    
    val aggR3 = AggregateReport(muid, aggFunction)
    aggR3 += RawPowerReport("topictest", muid, 3, 2.W, device, tickMock)
    
    render(aggR1)(eventBus)
    render(aggR2)(eventBus)
    render(aggR3)(eventBus)

    val testFile = Path.fromString(ConfigurationMock.testPath)
    testFile.isFile should be (true)
    testFile.size.get should be > 0L
    testFile.lines() should (
      have size 3 and
      contain(s"timestamp=${tickMock.timestamp};target=${intToProcess(1)};device=$device;value=${3.W}") and
      contain(s"timestamp=${tickMock.timestamp};target=${intToProcess(2)};device=$device;value=${1.W}") and
      contain(s"timestamp=${tickMock.timestamp};target=${intToProcess(3)};device=$device;value=${2.W}")
    )
    testFile.delete(true)
  }
}

