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
package org.powerapi.module.hwc

import com.paulgoldbaum.influxdbclient.Database
import com.twitter.zk.ZkClient
import org.influxdb.InfluxDB
import org.powerapi.PowerModule
import org.powerapi.core.{LinuxHelper, OSHelper}

import scala.concurrent.duration.FiniteDuration

class HWCCoreModule(osHelper: OSHelper, likwidHelper: LikwidHelper, cHelper: CHelper, events: Seq[String],
                    zkClient: ZkClient, influx: InfluxDB, influxDB: String, influxRp: String) extends PowerModule {

  val sensor = Some((classOf[HWCCoreSensor], Seq[Any](osHelper, likwidHelper, cHelper, events)))
  val formula = Some((classOf[HWCCoreFormula], Seq[Any](likwidHelper, zkClient, influx, influxDB, influxRp)))
}

object HWCCoreModule {
  def apply(prefixConfig: Option[String] = None, osHelper: OSHelper, likwidHelper: LikwidHelper, cHelper: CHelper, zkClient: ZkClient, influx: InfluxDB, influxDB: String, influxRp: String): HWCCoreModule = {
    val sensorConfig = new HWCCoreSensorConfiguration(prefixConfig)

    new HWCCoreModule(osHelper, likwidHelper, cHelper, sensorConfig.events, zkClient, influx, influxDB, influxRp)
  }
}

class HWCCoreSensorModule(osHelper: OSHelper, likwidHelper: LikwidHelper, cHelper: CHelper, events: Seq[String]) extends PowerModule {
  val sensor = Some((classOf[HWCCoreSensor], Seq[Any](osHelper, likwidHelper, cHelper, events)))
  val formula = None
}

object HWCCoreSensorModule {
  def apply(prefixConfig: Option[String] = None, osHelper: OSHelper, likwidHelper: LikwidHelper, cHelper: CHelper): HWCCoreSensorModule = {
    val sensorConfig = new HWCCoreSensorConfiguration(prefixConfig)

    new HWCCoreSensorModule(osHelper, likwidHelper, cHelper, sensorConfig.events)
  }
}
