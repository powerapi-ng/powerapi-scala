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
import java.util.concurrent.TimeUnit

import collection.JavaConversions._


import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.powerapi.PowerDisplay
import org.powerapi.core.power.Power
import org.powerapi.core.target.Target

/**
  * Write power information inside an InfluxDB database.
  */
class InfluxDisplay(host: String, user: String, pwd: String, dbName: String, measurement: String) extends PowerDisplay {

  val influxdb = InfluxDBFactory.connect(host, user, pwd)

  def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power) {
    val point = Point.measurement(measurement)
      .time(timestamp, TimeUnit.MILLISECONDS)
      .field("power", s"${power.toMilliWatts}")
      .tag(Map("muid" -> s"$muid", "targets" -> s"${targets.mkString(",")}", "devices" -> s"${devices.mkString(",")}"))
      .build()

    influxdb.write(dbName, "default", point)
  }
}
