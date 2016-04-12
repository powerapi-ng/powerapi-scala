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
package org.powerapi.osdi

import java.util.UUID
import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.io.Source
import scala.concurrent.duration.DurationLong

import com.github.dockerjava.core.DockerClientBuilder
import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.{Point, InfluxDB}
import org.powerapi.module.cpu.simple.CpuSimpleModule
import org.powerapi.{PowerMonitoring, PowerMeter, PowerDisplay}
import org.powerapi.core.power._
import org.powerapi.core.target.{All, Target, Container}
import org.powerapi.core.LinuxHelper

class InfluxDBCadvisor(monitoringF: Long, batchF: Long, host: String, port: Int, user: String, pwd: String, cAdvisorCid: String) extends PowerDisplay {

  val influxdb = InfluxDB.connect(host, port, user, pwd)
  val database = influxdb.selectDatabase("cadvisor")
  val points = scala.collection.mutable.ListBuffer[Point]()
  val timestamps = scala.collection.mutable.Set[Long]()

  def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power) {
    this.synchronized {
      if (timestamps.size < batchF.seconds.toMillis / monitoringF) {
        if (targets.size == 1) {
          val target = targets.head
          val containerName = targets.head match {
            case All => "/"
            case container: Container => container.name
          }

          val point = Point("power", timestamp)
            .addField("value", power.toMilliWatts)
            .addTag("container_name", s"$containerName")
            .addTag("machine", s"${cAdvisorCid.substring(0, 12)}")

          points += point
        }
      }

      else {
        database.bulkWrite(points, precision = Precision.MILLISECONDS)
        points.clear()
        timestamps.clear()
      }

      timestamps += timestamp
    }
  }
}

object Application extends App {

  val powerMeters = collection.mutable.ListBuffer[PowerMeter]()
  val monitors = collection.mutable.Map[String, PowerMonitoring]()

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    monitors.foreach {
      case (_, monitor) => monitor.cancel()
    }
    monitors.clear()
    powerMeters.foreach(powerMeter => powerMeter.shutdown())
    powerMeters.clear()
  }

  case class Config(monitoringF: Long = 1000, batchF: Long = 30, tdp: Double = -1, influxHost: String = "", influxPort: Int = -1, influxUser: String = "", influxPwd: String = "", cAdvisorCidPath: String = "")

  val parser = new scopt.OptionParser[Config]("scopt") {
    head("powerapi", "4.0")
    opt[Long]("monitoring_f") optional() action { (x, c) =>
      c.copy(monitoringF = x)
    } text "Monitoring frequency in ms"
    opt[Long]("batch_f") optional() action { (x, c) =>
      c.copy(batchF = x)
    } text "Batch frequency in s for cAdvisor"
    opt[Double]("tdp") required() action { (x, c) =>
      c.copy(tdp = x)
    } text "TDP value"
    opt[String]("influx_host") required() action { (x, c) =>
      c.copy(influxHost = x)
    } text "Influx DB host"
    opt[Int]("influx_port") required() action { (x, c) =>
      c.copy(influxPort = x)
    } text "Influx DB host"
    opt[String]("influx_user") required() action { (x, c) =>
      c.copy(influxUser = x)
    } text "Influx DB user"
    opt[String]("influx_pwd") required() action { (x, c) =>
      c.copy(influxPwd = x)
    } text "Influx DB pwd"
    opt[String]("cadvisor_cid_path") required() action { (x, c) =>
      c.copy(cAdvisorCidPath = x)
    } text "Cadvisor cid filepath"
    help("help") text "help message"
  }

  parser.parse(args, Config()) match {
    case Some(config) =>
      val docker = DockerClientBuilder.getInstance("unix:///var/run/docker.sock").build()
      val linuxHelper = new LinuxHelper()

      val powerapi = PowerMeter.loadModule(new CpuSimpleModule(linuxHelper, config.tdp, 0.7))
      powerMeters += powerapi
      val display = new InfluxDBCadvisor(config.monitoringF, config.batchF, config.influxHost, config.influxPort, config.influxUser, config.influxPwd, Source.fromFile(config.cAdvisorCidPath).getLines().next())

      monitors += "all" -> powerapi.monitor(All)(MAX).every(config.monitoringF.millis).to(display)

      val containers = docker.listContainersCmd().exec().map(container => Container(container.getId, container.getNames.mkString(",")))
      containers.foreach(container => monitors += container.id -> powerapi.monitor(container)(MAX).every(config.monitoringF.millis).to(display))
      Thread.sleep(1l.seconds.toMillis)

      while (true) {
        val currentContainers = docker.listContainersCmd().exec().map(container => Container(container.getId, container.getNames.mkString(",")))
        containers.diff(currentContainers).foreach(container => {
          monitors(container.id).cancel()
          monitors -= container.id
        })
        currentContainers.diff(containers).foreach(container => monitors += container.id -> powerapi.monitor(container)(MAX).every(1l.seconds).to(display))
        containers.clear()
        containers ++= currentContainers

        Thread.sleep(1l.seconds.toMillis)
      }

    case None =>

  }
}
