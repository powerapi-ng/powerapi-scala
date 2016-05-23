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

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.{InfluxDB, Point}
import org.powerapi.PowerDisplay
import org.powerapi.core.power.Power
import org.powerapi.core.target.Target
import org.powerapi.module.PowerChannel.AggregatePowerReport

/**
  * Write power information inside an InfluxDB database.
  */
class InfluxDisplay(host: String, port: Int, user: String, pwd: String, dbName: String, measurement: String) extends PowerDisplay {

  val influxdb = InfluxDB.connect(host, port, user, pwd)
  val database = influxdb.selectDatabase(dbName)

  def display(aggregatePowerReport: AggregatePowerReport): Unit = {
    val muid = aggregatePowerReport.muid
    val timestamp = aggregatePowerReport.ticks.map(_.timestamp).head
    val targets = aggregatePowerReport.targets
    val devices = aggregatePowerReport.devices
    val power = aggregatePowerReport.power

    val point = Point(measurement, timestamp)
      .addField("power", power.toMilliWatts)
      .addTag("muid", s"$muid")
      .addTag("targets", s"${targets.mkString(",")}")
      .addTag("devices", s"${devices.mkString(",")}")

    database.write(point, precision = Precision.MILLISECONDS)
  }
}
