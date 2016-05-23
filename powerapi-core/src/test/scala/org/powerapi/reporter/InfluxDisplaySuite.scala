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
package org.powerapi.reporter

import java.util.UUID

import scala.concurrent.duration.DurationInt
import akka.util.Timeout
import org.joda.time.format.ISODateTimeFormat
import org.powerapi.UnitTest
import org.powerapi.core.Tick
import org.powerapi.core.power._
import org.powerapi.core.target.{Application, Process, Target}
import org.powerapi.module.PowerChannel.AggregatePowerReport
import org.scalatest.time.{Seconds, Span}

class InfluxDisplaySuite extends UnitTest {

  val timeout = Timeout(10.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  "An InfluxDisplay" should "write an AggPowerReport message in a database" in {
    val muid = UUID.randomUUID()
    val baseTick = new Tick {
      val topic = ""
      val timestamp = System.currentTimeMillis()
    }
    val baseTargets = Set[Target](Application("firefox"), Process(1), Process(2))
    val baseDevices = Set[String]("cpu", "gpu", "ssd")
    val basePower = 10.W

    val aggregatePowerReport = new AggregatePowerReport(muid) {
      override def ticks = Set(baseTick)
      override def targets = baseTargets
      override def devices = baseDevices
      override def power = basePower
    }

    val influxDisplay = new InfluxDisplay("localhost", 8086, "powerapi", "powerapi", "test", "event.powerapi")

    whenReady(influxDisplay.database.create(), timeout(Span(30, Seconds))) {
      _ =>
        influxDisplay.display(aggregatePowerReport)

        awaitCond({
          whenReady(influxDisplay.database.query("SELECT * FROM \"event.powerapi\"")) {
            result =>
              result.series.size == 1 &&
              result.series.head.records.size == 1 &&
              ISODateTimeFormat.dateTimeParser().parseDateTime(result.series.head.records.head("time").toString).getMillis == baseTick.timestamp &&
              result.series.head.records.head("devices") == baseDevices.mkString(",") &&
              result.series.head.records.head("muid") == s"$muid" &&
              result.series.head.records.head("power") == basePower.toMilliWatts &&
              result.series.head.records.head("targets") == baseTargets.mkString(",")
          }
        }, 30.seconds, 1.seconds)

        whenReady(influxDisplay.database.drop(), timeout(Span(30, Seconds))) {
          _ =>
            assert(true)
        }
    }
  }
}

