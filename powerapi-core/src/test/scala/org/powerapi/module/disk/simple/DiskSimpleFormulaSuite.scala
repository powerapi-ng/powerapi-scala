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
package org.powerapi.module.disk.simple

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout
import org.joda.time.DateTimeUtils
import org.powerapi.UnitTest
import org.powerapi.core.power._
import org.powerapi.core.target.{Target, intToProcess}
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.FormulaChannel.{startFormula, stopFormula}
import org.powerapi.module.Formulas
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.disk.UsageMetricsChannel.{DiskUsageReport, TargetDiskUsageRatio, publishDiskUsageReport}

class DiskSimpleFormulaSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A DiskSimpleFormula" should "process a SensorReport and then publish a RawPowerReport" in new Bus {
    val formulae = Map[String, Map[String, Seq[PieceWiseFunction]]](
      "sda" -> Map(
        "read" -> Seq(PieceWiseFunction(Condition("<= 78966784"), Seq(0.00, 1.01e-07)), PieceWiseFunction(Condition("> 78966784"), Seq(7.62, 1.72e-10))),
        "write" -> Seq(PieceWiseFunction(Condition("<= 66674688"), Seq(0.00, 1.13e-07)), PieceWiseFunction(Condition("> 66674688"), Seq(8.33, 1.79e-09)))
      ),
      "sdb" -> Map(
        "read" -> Seq(PieceWiseFunction(Condition("<= 500000"), Seq(0.00, 1e-10)), PieceWiseFunction(Condition("> 78966784"), Seq(5.5, 1.22e-12))),
        "write" -> Seq(PieceWiseFunction(Condition("<= 1200000"), Seq(0.00, 1.02e-10)), PieceWiseFunction(Condition("> 1200000"), Seq(7.3, 1.55e-11)))
      )
    )
    val muid = UUID.randomUUID()
    val target: Target = 1

    val bytesReadSda = 1000 * 1024 * 1024
    val bytesWrittenSda = 40 * 1024 * 1024
    val bytesWrittenSdb = 1000 * 1024 * 1024

    def scaleUsages(ratio: Double): Seq[TargetDiskUsageRatio] = {
      Seq(
        TargetDiskUsageRatio("sda", (bytesReadSda * ratio).toLong, 0.5, (bytesWrittenSda * ratio).toLong, 1.0),
        TargetDiskUsageRatio("sdb", 0, 0, (bytesWrittenSdb * ratio).toLong, 0.65)
      )
    }

    def computePower(usages: Seq[TargetDiskUsageRatio], samplingInterval: FiniteDuration, monitoringInterval: FiniteDuration): Power = {
      val bytesReadSda = usages(0).bytesRead * (samplingInterval.toMillis / monitoringInterval.toMillis.toDouble)
      val bytesWrittenSda = usages(0).bytesWritten * (samplingInterval.toMillis / monitoringInterval.toMillis.toDouble)
      val bytesWrittenSdb = usages(1).bytesWritten * (samplingInterval.toMillis / monitoringInterval.toMillis.toDouble)

      ((formulae("sda")("read")(1).coeffs(0) + (formulae("sda")("read")(1).coeffs(1) * bytesReadSda)) * 0.5 +
        (formulae("sda")("write")(0).coeffs(0) + (formulae("sda")("write")(0).coeffs(1) * bytesWrittenSda)) * 1.0 +
          (formulae("sdb")("write")(1).coeffs(0) + (formulae("sdb")("write")(1).coeffs(1) * bytesWrittenSdb)) * 0.65).W
    }

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }

    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val old = System.currentTimeMillis()
    val formula1 = TestActorRef(Props(classOf[DiskSimpleFormula], eventBus, muid, target, 1.second, formulae))
    subscribeRawPowerReport(muid)(eventBus)(testActor)

    DateTimeUtils.setCurrentMillisFixed(old + 1000)
    val usages = scaleUsages(1.0)
    formula1.underlyingActor.asInstanceOf[DiskSimpleFormula].compute(old)(DiskUsageReport("", muid, target, usages, tick1))
    var rawPowerReport = expectMsgClass(classOf[RawPowerReport])
    rawPowerReport.muid should equal(muid)
    rawPowerReport.target should equal(target)
    rawPowerReport.power should equal(computePower(usages, 1.second, 1.second))
    rawPowerReport.device should equal("disk")
    rawPowerReport.tick should equal(tick1)
    DateTimeUtils.setCurrentMillisFixed(old + 250)
    formula1.underlyingActor.asInstanceOf[DiskSimpleFormula].compute(old)(DiskUsageReport("", muid, target, usages, tick1))
    rawPowerReport = expectMsgClass(classOf[RawPowerReport])
    rawPowerReport.muid should equal(muid)
    rawPowerReport.target should equal(target)
    rawPowerReport.power should equal(computePower(usages, 1.second, 250.millis))
    rawPowerReport.device should equal("disk")
    rawPowerReport.tick should equal(tick1)
    DateTimeUtils.setCurrentMillisFixed(old + 4000)
    formula1.underlyingActor.asInstanceOf[DiskSimpleFormula].compute(old)(DiskUsageReport("", muid, target, usages, tick1))
    rawPowerReport = expectMsgClass(classOf[RawPowerReport])
    rawPowerReport.muid should equal(muid)
    rawPowerReport.target should equal(target)
    rawPowerReport.power should equal(computePower(usages, 1.second, 4.seconds))
    rawPowerReport.device should equal("disk")
    rawPowerReport.tick should equal(tick1)

    Await.result(gracefulStop(formula1, timeout.duration), timeout.duration)
  }
}
