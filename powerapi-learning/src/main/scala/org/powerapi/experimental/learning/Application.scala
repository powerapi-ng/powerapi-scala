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
package org.powerapi.experimental.learning

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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import com.github.dockerjava.core.DockerClientBuilder
import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.{Database, InfluxDB, Point}
import com.twitter.util.{Duration, JavaTimer}
import com.twitter.zk.ZkClient
import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import org.powerapi.core.LinuxHelper
import org.powerapi.core.target.{All, Container}
import org.powerapi.module.PowerChannel.AggregatePowerReport
import org.powerapi.module.hwc.{CHelper, HWCCoreModule, HWCCoreSensorModule, LikwidHelper, PowerData, RAPLDomain}
import org.powerapi.core.power._
import org.powerapi.module.hwc.HWCChannel.{HWC, HWCReport, subscribeHWCReport}
import org.powerapi.module.hwc.RAPLDomain.RAPLDomain
import org.powerapi.module.rapl.{RaplCpuModule, RaplDramModule}
import org.powerapi.{PowerDisplay, PowerMeter, PowerMonitoring}

import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys
import scala.util.{Failure, Success}

class InfluxDBMonitoring(db: Database, measurement: String, likwidHelper: LikwidHelper) extends Actor with ActorLogging {

  // One core per socket for RAPL (no need to monitor each core)
  private var cores: Option[Seq[Int]] = None

  likwidHelper.topologyInit()
  likwidHelper.affinityInit()

  cores = Some(
    likwidHelper.getAffinityDomains().domains
      .filter(_.tag.startsWith("S"))
      .map(_.processorList.head)
  )

  // Only on the first core, no need to call powerInit on all cores
  likwidHelper.powerInit(0)

  cores.get foreach likwidHelper.HPMaddThread

  override def postStop(): Unit = {
    likwidHelper.powerFinalize()
    cores = None
  }

  def receive: Actor.Receive = {
    val data = startCollect()
    sense(data)
  }

  def startCollect(): Map[(Int, RAPLDomain), PowerData] = {
    (for {
      core <- cores.get
      domain <- RAPLDomain.values.toSeq
    } yield {
      ((core, domain), likwidHelper.powerStart(core, domain))
    }).toMap
  }

  def stopCollect(data: Map[(Int, RAPLDomain), PowerData]): Map[RAPLDomain, Seq[Double]] = {
    var results = Map[RAPLDomain, Seq[Double]]()

    for (((core, domain), pData) <- data) {
      val powerData = likwidHelper.powerStop(pData, core)
      results += domain -> (results.getOrElse(domain, Seq()) :+ likwidHelper.getEnergy(powerData))
    }

    results
  }

  def sense(data: Map[(Int, RAPLDomain), PowerData]): Actor.Receive = {
    case msg: HWCReport =>
      val results = stopCollect(data)
      val raplCpu = results(RAPLDomain.PKG)
      val raplDram = results(RAPLDomain.DRAM)

      val points = for ((socket, values) <- msg.values.groupBy(_.hwThread.packageId)) yield {
        var point = Point(measurement, msg.tick.timestamp)
          .addField("cpu", raplCpu(socket))
          .addField("dram", raplDram(socket))
          .addTag("socket", s"S$socket")

        var index = 0
        for ((core, hwc) <- values.groupBy(_.hwThread.coreId)) {
          assert(hwc.size == 4)
          val cycles = hwc.filter(_.event == "CPU_CLK_UNHALTED_CORE:FIXC1").foldLeft(0d)((acc, hwc) => hwc.value)
          val refs = hwc.filter(_.event == "CPU_CLK_UNHALTED_REF:FIXC2").foldLeft(0d)((acc, hwc) => hwc.value)

          point = point.addField(s"c$index", cycles)
          point = point.addField(s"r$index", refs)
          index += 1
        }

        point
      }

      db.bulkWrite(points.toSeq, precision = Precision.MILLISECONDS)

      context.become(sense(startCollect()))
  }
}

class InfluxDBReporting(db: Database, transform: AggregatePowerReport => Point) extends PowerDisplay {

  @volatile var timestamps = Set[Long]()
  @volatile var points = Seq[Point]()

  def display(aggregatePowerReport: AggregatePowerReport) {
    timestamps += aggregatePowerReport.ticks.head.timestamp
    points :+= transform(aggregatePowerReport)

    if (timestamps.size > 60) {
      db.bulkWrite(points, precision = Precision.MILLISECONDS)
      points = Seq()
      timestamps = Set()
    }
  }
}

object Application extends App {

  @volatile var running = true

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    running = false
  }

  case class Config(mode: String = "", zkUrl: String = "", zkTimeout: Int = 10, influxHost: String = "", influxPort: Int = 8086, influxDatabase: String = "", duration: Int = 15)

  val parser = new scopt.OptionParser[Config]("active-learning") {
    cmd("idle") action { (_, c) =>
      c.copy(mode = "idle")
    } children {
      opt[String]("influxH") action { (x, c) =>
        c.copy(influxHost = x)
      } text "InfluxDB host"
      opt[Int]("influxP") action { (x, c) =>
        c.copy(influxPort = x)
      } text "InfluxDB port"
      opt[String]("influxDB") action { (x, c) =>
        c.copy(influxDatabase = x)
      } text "InfluxDB database"
      opt[Int]("duration") action { (x, c) =>
        c.copy(duration = x)
      } text "Collecting duration (s)"
    }

    cmd("active") action { (_, c) =>
      c.copy(mode = "active")
    } children {
      opt[String]("zkUrl") action { (x, c) =>
        c.copy(zkUrl = x)
      } text "ZooKeeper url"
      opt[Int]("zkTimeout") action { (x, c) =>
        c.copy(zkTimeout = x)
      } text "ZooKeeper session timeout"
      opt[String]("influxH") action { (x, c) =>
        c.copy(influxHost = x)
      } text "InfluxDB host"
      opt[Int]("influxP") action { (x, c) =>
        c.copy(influxPort = x)
      } text "InfluxDB port"
      opt[String]("influxDB") action { (x, c) =>
        c.copy(influxDatabase = x)
      } text "InfluxDB database"
    }

    checkConfig { c =>
      if (c.mode == "") failure("Choose idle or active mode.")
      else success
    }
  }

  parser.parse(args, Config()) match {
    case Some(config) =>
      var pMeters = Seq[PowerMeter]()
      var monitors = Map[String, PowerMonitoring]()
      var actors = Seq[ActorRef]()

      val system = ActorSystem("learning")

      val influxdb = InfluxDB.connect(config.influxHost, config.influxPort, "", "")
      val db = influxdb.selectDatabase(config.influxDatabase)

      val hostname = Source.fromFile("/proc/sys/kernel/hostname").mkString.trim
      val docker = DockerClientBuilder.getInstance("unix:///var/run/docker.sock").build()

      val osHelper = new LinuxHelper()
      val likwidHelper = new LikwidHelper()
      likwidHelper.useDirectMode()
      val cHelper = new CHelper()

      likwidHelper.topologyInit()
      likwidHelper.affinityInit()

      if (config.mode == "idle") {
        val papiMonitoring = PowerMeter.loadModule(HWCCoreSensorModule(None, osHelper, likwidHelper, cHelper))
        pMeters :+= papiMonitoring

        val monitoringRef = system.actorOf(Props(classOf[InfluxDBMonitoring], db, "idle", likwidHelper))
        actors :+= monitoringRef
        val monitor = papiMonitoring.monitor(All)(MAX).every(1.second)
        monitors += hostname -> monitor.to(monitoringRef, subscribeHWCReport(monitor.muid, All))

        Thread.sleep(config.duration * 1000)
      }

      else if (config.mode == "active") {
        implicit val timer = new JavaTimer(true)
        val zkClient = ZkClient(config.zkUrl, Duration.fromSeconds(config.zkTimeout)).withAcl(OPEN_ACL_UNSAFE.asScala)

        val papiMonitoring = PowerMeter.loadModule(HWCCoreSensorModule(None, osHelper, likwidHelper, cHelper))
        pMeters :+= papiMonitoring

          // TODO
//        val monitoringRef = system.actorOf(Props(classOf[InfluxDBMonitoring], db, "active", likwidHelper))
//        actors :+= monitoringRef
//        val monitor = papiMonitoring.monitor(All)(MAX).every(1.second)
//        monitors += hostname -> monitor.to(monitoringRef, subscribeHWCReport(monitor.muid, All))

        val papiReporting = PowerMeter.loadModule(HWCCoreModule(None, osHelper, likwidHelper, cHelper, zkClient), RaplCpuModule(likwidHelper), RaplDramModule(likwidHelper))
        pMeters :+= papiReporting

        val hostMeasurement = new InfluxDBReporting(db, (report: AggregatePowerReport) => {
          val timestamp = report.ticks.head.timestamp
          val powerPerDevice = report.powerPerDevice
          val est = powerPerDevice("cpu")
          val raplCpu = powerPerDevice("rapl-cpu")
          val raplDram = powerPerDevice("rapl-dram")

          Point(hostname, timestamp)
            .addField("powerapi", est.toWatts)
            .addField("cpu", raplCpu.toWatts)
            .addField("dram", raplDram.toWatts)
        })

        val containerMeasurement = new InfluxDBReporting(db, (report: AggregatePowerReport) => {
          val timestamp = report.ticks.head.timestamp
          val powerPerDevice = report.powerPerDevice
          val name = report.targets.head.asInstanceOf[Container].name
          val est = powerPerDevice("cpu")

          Point(name, timestamp)
            .addField("powerapi", est.toWatts)
        })

        monitors += hostname -> papiReporting.monitor(All)(MAX).every(1.second).to(hostMeasurement)

        val containers = docker.listContainersCmd().exec().asScala.map(container => Container(container.getId, container.getNames.mkString(",")))
        containers.foreach(container => monitors += container.id -> papiReporting.monitor(container)(MAX).every(1.second).to(containerMeasurement))
        Thread.sleep(1.second.toMillis)

        while (running) {
          val currentContainers = docker.listContainersCmd().exec().asScala.map(container => Container(container.getId, container.getNames.mkString(",")))
          containers.diff(currentContainers).foreach(container => {
            monitors(container.id).cancel()
            monitors -= container.id
          })
          currentContainers.diff(containers).foreach(container => monitors += container.id -> papiReporting.monitor(container)(MAX).every(1.second).to(containerMeasurement))
          containers.clear()
          containers ++= currentContainers

          Thread.sleep(1.second.toMillis)
        }

        zkClient.release()
      }

      likwidHelper.topologyFinalize()
      likwidHelper.affinityFinalize()

      influxdb.close()

      monitors.foreach {
        case (_, monitor) => monitor.cancel()
      }
      pMeters.foreach(powerMeter => powerMeter.shutdown())

      for (actor <- actors) actor ! PoisonPill

      Await.result(system.terminate(), 10.seconds)

    case None =>

  }

  sys.exit(0)
}
