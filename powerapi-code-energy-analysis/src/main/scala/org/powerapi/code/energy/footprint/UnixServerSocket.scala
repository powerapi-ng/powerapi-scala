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
package org.powerapi.code.energy.footprint

import java.io.{BufferedReader, File, IOException, InputStreamReader}
import java.nio.channels.Channels

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.{InfluxDB, Point}
import com.typesafe.config.Config

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import collection.JavaConversions._
import jnr.unixsocket.{UnixServerSocketChannel, UnixSocketAddress, UnixSocketChannel}
import org.apache.logging.log4j.LogManager
import org.powerapi.core.{ConfigValue, Configuration}
import org.powerapi.module.libpfm.PayloadProtocol.Payload
import org.powerapi.{PowerDisplay, PowerMeter, PowerMonitoring}
import org.powerapi.core.power._
import org.powerapi.core.TickChannel.{publishTick, tickTopic}
import org.powerapi.core.target.{Application, Process}
import org.powerapi.module.PowerChannel.AggregatePowerReport
import org.powerapi.module.libpfm.AgentTick
import org.powerapi.module.libpfm.PCInterruptionChannel.InterruptionTick

import scala.concurrent.Await

class InterruptionInfluxDisplay(host: String, port: Int, user: String, pwd: String, dbName: String, measurement: String) extends PowerDisplay {
  val timeout = 10.seconds
  val influxdb = InfluxDB.connect(host, port, user, pwd)
  val database = influxdb.selectDatabase(dbName)

  val measurements = Await.result(database.query("show measurements"), timeout).series

  val run = {
    if (measurements.nonEmpty && measurements.head.records.map(record => record("name").toString).contains(measurement)) {
      val last = Await.result(database.query(s"select last(cpu), run from $measurement"), timeout).series.head.points("run")
      if (last.isEmpty) 1 else last.head.toString.toInt + 1
    }
    else 1
  }

  override def display(aggregatePowerReport: AggregatePowerReport): Unit = {
    val rawPowers = aggregatePowerReport.rawPowers
    val disk = rawPowers.filter(_.tick.isInstanceOf[AgentTick]).map(_.power.toWatts).sum

    val points = for (rawPower <- rawPowers.filter(_.tick.isInstanceOf[InterruptionTick])) yield {
      val interruptionTick = rawPower.tick.asInstanceOf[InterruptionTick]
      val cpu = rawPower.power.toWatts

      Point(measurement, interruptionTick.timestamp)
        .addField("cpu", cpu)
        .addField("disk", if (interruptionTick.triggering) disk else 0.0)
        .addTag("core", s"${interruptionTick.cpu}")
        .addTag("method", s"${interruptionTick.fullMethodName}")
        .addTag("run", s"$run")
        .addTag("tid", s"${interruptionTick.tid}")
    }

    database.bulkWrite(points, precision = Precision.NANOSECONDS)
  }

  def cancel(): Unit = {
    database.close()
  }
}

/**
  * Client of a PowerAPI's agent on the DataSocket for getting all sent payloads and
  * for mapping them to special ticks for the internal components of PowerAPI.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class DataRequest(dataChannel: UnixSocketChannel, monitor: PowerMonitoring) extends Thread {
  private val log = LogManager.getLogger
  @volatile var running = true

  val stream = Channels.newInputStream(dataChannel)

  override def run(): Unit = {
    while (running) {
      try {
        val sizeBytes: Array[Byte] = Array.fill(4){0}
        stream.read(sizeBytes, 0, sizeBytes.length)
        val size = ntohl(sizeBytes)
        val payloadBytes: Array[Byte] = Array.fill(size){0}
        stream.read(payloadBytes, 0, payloadBytes.length)
        val payload = Payload.parseFrom(payloadBytes)
        val tick = AgentTick(tickTopic(monitor.muid), payload.getTimestamp, payload)
        publishTick(tick)(monitor.eventBus)
      }
      catch {
        case _: IOException =>
        case _: OutOfMemoryError =>
        case ex: Throwable => log.error(ex)
      }
    }
  }

  def ntohl(x: Array[Byte]): Int = {
    var res = 0

    for (i <- 0 until 4) {
      res <<= 8
      res |= x(i).toInt
    }

    res
  }

  def cancel(): Unit = {
    running = false
    try {
      join(1.seconds.toMillis)
    }
    catch {
      case _: InterruptedException =>
    }
    finally {
      stream.close()
      dataChannel.close()
    }
  }
}

/**
  * Represent a connexion to the ControlSocket and connect to the DataSocket.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class ControlRequest(controlChannel: UnixSocketChannel, configuration: UnixServerSocketConfiguration, pMeter: PowerMeter) extends Thread {
  private val log = LogManager.getLogger
  @volatile var running = true

  val reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(controlChannel)))
  //val PID = """^(\d+)\s?$""".r
  val pids = collection.mutable.ArrayBuffer[Int]()
  val requests = collection.mutable.ListBuffer[DataRequest]()
  var monitor: Option[PowerMonitoring] = None
  var display: Option[InterruptionInfluxDisplay] = None

  override def run(): Unit = {
    try {
      val software = reader.readLine()
      /*val line = reader.readLine()
      val pid = line match {
        case PID(p) => p.toInt
      }*/
      display = Some(new InterruptionInfluxDisplay(configuration.influxHost, configuration.influxPort, configuration.influxUser, configuration.influxPwd, configuration.influxDB, software))
      monitor = Some(pMeter.monitor(Application(software))(MAX).to(display.get))

      requests ++= {
        for (core <- configuration.topology.values.flatten) yield {
          val path = new File(s"/tmp/agent-$core-$software.sock")
          val address = new UnixSocketAddress(path)
          new DataRequest(UnixSocketChannel.open(address), monitor.get)
        }
      }

      requests.foreach(_.start())
      // Expects a message when the app is stopped (any kind of message)
      reader.readLine()
      requests.foreach(_.cancel())
      display.get.cancel()
      monitor.get.cancel()
      reader.close()
    }
    catch {
      case _: IOException =>
      case ex: Throwable => log.error(ex)
    }
  }

  def cancel(): Unit = {
    try {
      join(1.seconds.toMillis)
    }
    catch {
      case _: InterruptedException =>
    }
    finally {
      requests.foreach(_.cancel())
      monitor.get.cancel()
      reader.close()
    }
  }
}

class UnixServerSocketConfiguration extends Configuration(None) {
  lazy val topology: Map[Int, Set[Int]] = load { conf =>
    (for (item: Config <- conf.getConfigList("powerapi.cpu.topology"))
      yield (item.getInt("core"), item.getIntList("indexes").map(_.toInt).toSet)).toMap
  } match {
    case ConfigValue(values) => values
    case _ => Map()
  }

  lazy val influxHost: String = load { conf =>
    conf.getString("powerapi.influx.host")
  } match {
    case ConfigValue(value) => value
    case _ => ""
  }

  lazy val influxPort: Int = load { conf =>
    conf.getInt("powerapi.influx.port")
  } match {
    case ConfigValue(value) => value
    case _ => 8086
  }

  lazy val influxUser: String = load { conf =>
    conf.getString("powerapi.influx.username")
  } match {
    case ConfigValue(value) => value
    case _ => ""
  }

  lazy val influxPwd: String = load { conf =>
    conf.getString("powerapi.influx.pwd")
  } match {
    case ConfigValue(value) => value
    case _ => ""
  }

  lazy val influxDB: String = load { conf =>
    conf.getString("powerapi.influx.database")
  } match {
    case ConfigValue(value) => value
    case _ => ""
  }
}

/**
  * A UnixSocketServer is responsible to open a control flow connexion for PowerAPI agents.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class UnixServerSocket(pMeter: PowerMeter) extends Thread {
  private val log = LogManager.getLogger
  @volatile var running = true

  val path = new File("/tmp/agent-control.sock")
  val address = new UnixSocketAddress(path)
  val server = UnixServerSocketChannel.open()
  server.socket().bind(address)
  val requests = collection.mutable.ListBuffer[ControlRequest]()
  val configuration = new UnixServerSocketConfiguration()

  override def run(): Unit = {
    while (running) {
      try {
        val request = new ControlRequest(server.accept(), configuration, pMeter)
        request.start()
        requests += request
      }
      catch {
        case _: IOException =>
        case ex: Throwable => log.error(ex.getMessage)
      }
    }
  }

  def cancel(): Unit = {
    running = false
    try {
      join(1.seconds.toMillis)
    }
    catch {
      case _: InterruptedException =>
    }
    finally {
      requests.foreach(request => {
        request.cancel()
      })
      server.close()
      path.delete()
    }
  }
}
