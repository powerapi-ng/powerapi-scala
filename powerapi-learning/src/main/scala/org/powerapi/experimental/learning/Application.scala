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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import com.github.dockerjava.core.DockerClientBuilder
import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.{Database, InfluxDB, Point}
import com.twitter.util.{Duration, JavaTimer}
import com.twitter.zk.ZkClient
import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import org.influxdb.InfluxDBFactory
import org.powerapi.core.{LinuxHelper, Message}
import org.powerapi.core.target.{All, Container}
import org.powerapi.module.PowerChannel.{AggregatePowerReport, RawPowerReport, subscribeAggPowerReport}
import org.powerapi.module.hwc.{CHelper, HWCCoreModule, HWCCoreSensorModule, LikwidHelper, PowerData, RAPLDomain, ZKTick}
import org.powerapi.core.power._
import org.powerapi.module.hwc.HWCChannel.{HWC, HWCReport, subscribeHWCReport}
import org.powerapi.module.hwc.RAPLDomain.RAPLDomain
import org.powerapi.{PowerDisplay, PowerMeter, PowerMonitoring}
import org.powerapi.core.TickChannel.{publishTick, tickTopic}
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.module.rapl.{RAPLCpuModule, RAPLDramModule}

import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys
import scala.util.{Failure, Success}

class InfluxDBMonitoring(db: Database, measurement: String, retentionPolicy: Option[String]) extends Actor with ActorLogging {

  def receive = accumulator(Map())

  def accumulator(acc: Map[Long, Seq[Message]]): Actor.Receive = {
    case msg: Message if msg.isInstanceOf[HWCReport] || msg.isInstanceOf[AggregatePowerReport] =>
      val timestamp = msg match {
        case r: HWCReport => r.tick.timestamp
        case r: AggregatePowerReport => r.ticks.head.timestamp
      }

      acc.get(timestamp) match {
        case Some(values) =>
          val messages = values :+ msg
          assert(messages.size == 2)

          val hwcReport = messages.filter(_.isInstanceOf[HWCReport]).head.asInstanceOf[HWCReport]
          val allReport = messages.filter(_.isInstanceOf[AggregatePowerReport]).head.asInstanceOf[AggregatePowerReport]
          val raplPowers = allReport.powerPerDevice

          val points = (for ((socket, hwThreads) <- hwcReport.values.groupBy(_.hwThread.packageId)) yield {
            var point = Point(measurement, timestamp)
              .addField("gcpu", raplPowers(s"rapl-cpu-S$socket").toWatts)
              .addField("gdram", raplPowers(s"rapl-dram-S$socket").toWatts)
              .addTag("socket", s"S$socket")

            var index = 0
            for ((core, hwcs) <- ListMap(hwThreads.groupBy(_.hwThread.coreId).toSeq.sortBy(_._1): _*)) {
              assert(hwcs.size == 4)
              val cycles = hwcs.filter(_.event == "CPU_CLK_UNHALTED_CORE:FIXC1").map(_.value).sum
              val refs = hwcs.filter(_.event == "CPU_CLK_UNHALTED_REF:FIXC2").map(_.value).sum

              point = point.addField(s"c$index", cycles)
              point = point.addField(s"r$index", refs)
              index += 1
            }

            point
          }).toSeq

          retentionPolicy match {
            case Some(rp) =>
              db.bulkWrite(points, precision = Precision.MILLISECONDS, retentionPolicy = rp) onComplete {
                case Success(result) =>
                  log.info("Flushing points: OK")
                case Failure(t) =>
                  log.error("Flushing points: KO")
              }
            case None =>
              db.bulkWrite(points, precision = Precision.MILLISECONDS) onComplete {
                case Success(result) =>
                  log.info("Flushing points: OK")
                case Failure(t) =>
                  log.error("Flushing points: KO")
              }
          }

          context.become(accumulator(acc - timestamp))

        case None =>
          context.become(accumulator(acc + (timestamp -> Seq(msg))))
      }
  }
}

class InfluxDBReporting(db: Database, measurement: String, hostname: String, retentionPolicy: Option[String]) extends Actor with ActorLogging {

  def receive = accumulator(Seq(), Set(), Seq())

  def accumulator(points: Seq[Point], timestamps: Set[Long], acc: Seq[AggregatePowerReport]): Actor.Receive = {
    case report: AggregatePowerReport =>
      val timestamp = report.ticks.head.timestamp

      if (timestamps.size > 0 && !timestamps.contains(timestamp)) {
        var _points = Seq[Point]()
        val previousTimestamp = acc.head.ticks.head.timestamp

        val (cpuIdle, hash) = acc.map(_.ticks.filter(_.isInstanceOf[ZKTick]).map(tick => {val zkTick = tick.asInstanceOf[ZKTick]; (zkTick.cpuIdle, zkTick.formulaeHash)}).find(_._2 != "none")).head.getOrElse(0.0, "none")

        val allPowers = acc.filter(_.targets.head == All).map(_.powerPerDevice).foldLeft(Map[String, Power]())((acc, map) => acc.++:(map))
        val gcpuPower = allPowers.filter(_._1.startsWith("rapl-cpu")).values.foldLeft(0.W)((acc, p) => acc + p)
        val gdramPower = allPowers.filter(_._1.startsWith("rapl-dram")).values.foldLeft(0.W)((acc, p) => acc + p)
        val vcpuPower = allPowers.filter(_._1.startsWith("cpu")).values.foldLeft(0.W)((acc, p) => acc + p)
        val vdramPower = allPowers.filter(_._1.startsWith("dram")).values.foldLeft(0.W)((acc, p) => acc + p)

        _points :+= Point(measurement, previousTimestamp)
          .addField("gcpu", gcpuPower.toWatts)
          .addField("gdram", gdramPower.toWatts)
          .addField("vcpu", vcpuPower.toWatts)
          .addField("vdram", vdramPower.toWatts)
          .addField("vcpuidle", cpuIdle)
          .addTag("target", "All")
          .addTag("node", hostname)
          .addTag("hash", hash)

        val containerReports = acc.filter(_.targets.head.isInstanceOf[Container])

        _points ++:= (for (report <- containerReports) yield {
          val containerPowers = report.powerPerDevice
          val vcpuPower = containerPowers.getOrElse("cpu", 0.W)
          val vdramPower = containerPowers.getOrElse("dram", 0.W)

          Point(measurement, previousTimestamp)
            .addField("gcpu", gcpuPower.toWatts)
            .addField("gdram", gdramPower.toWatts)
            .addField("vcpu", vcpuPower.toWatts)
            .addField("vdram", vdramPower.toWatts)
            .addField("vcpuidle", cpuIdle)
            .addTag("target", report.targets.head.asInstanceOf[Container].name)
            .addTag("node", hostname)
            .addTag("hash", hash)
        })

        if (timestamps.size + 1 > 60) {
          retentionPolicy match {
            case Some(rp) =>
              db.bulkWrite(points ++ _points, precision = Precision.MILLISECONDS, retentionPolicy = rp) onComplete {
                case Success(result) =>
                  log.info("Flushing points: OK")
                case Failure(t) =>
                  log.error("Flushing points: KO")
              }
            case None =>
              db.bulkWrite(points ++ _points, precision = Precision.MILLISECONDS) onComplete {
                case Success(result) =>
                  log.info("Flushing points: OK")
                case Failure(t) =>
                  log.error("Flushing points: KO")
              }
          }

          context.become(accumulator(Seq(), Set(), Seq()))
        }

        else context.become(accumulator(points ++ _points, timestamps + timestamp, Seq(report)))
      }

      else context.become(accumulator(points, timestamps + timestamp, acc :+ report))
  }
}

object Application extends App {

  @volatile var running = true

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    running = false
  }

  case class Config(mode: String = "", zkUrl: String = "", zkTimeout: Int = 10, influxHost: String = "", influxPort: Int = 8086, influxDatabase: String = "", influxRp: Option[String] = None, duration: Int = 15, hostnameFilepath: String = "/etc/hostname")

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
      opt[String]("influxRp") action { (x, c) =>
        c.copy(influxRp = Some(x))
      } text "InfluxDB database"
      opt[String]("hostnameFp") action { (x, c) =>
        c.copy(hostnameFilepath = x)
      } text "Hostname filepath"
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

      val hostname = Source.fromFile(config.hostnameFilepath).mkString.trim
      val docker = DockerClientBuilder.getInstance("unix:///var/run/docker.sock").build()

      val osHelper = new LinuxHelper()
      val likwidHelper = new LikwidHelper()
      likwidHelper.useDirectMode()
      val cHelper = new CHelper()

      likwidHelper.topologyInit()
      likwidHelper.affinityInit()

      /** RAPL INIT */
      // Only on the first core, no need to call powerInit on all cores
      likwidHelper.powerInit(0)
      likwidHelper.getAffinityDomains().domains
        .filter(_.tag.startsWith("S"))
        .map(_.processorList.head)
        .foreach(core => likwidHelper.HPMaddThread(core))

      /** PERFMON INIT */
      if (likwidHelper.perfmonInit(likwidHelper.getCpuTopology().threadPool.map(_.apicId)) < 0) {
        println("Failed to initialize LIKWID's performance monitoring module")
        sys.exit(1)
      }

      val clockTimer = new JavaTimer(true)

      clockTimer.schedule(Duration.fromSeconds(1)) {
        val timestamp = System.currentTimeMillis()
        for ((_, monitor) <- monitors) publishTick(ClockTick(tickTopic(monitor.muid), timestamp))(monitor.eventBus)
      }

      if (config.mode == "idle") {
        val papiReporting = PowerMeter.loadModule(
          HWCCoreSensorModule(None, osHelper, likwidHelper, cHelper),
          RAPLCpuModule(likwidHelper),
          RAPLDramModule(likwidHelper)
        )
        pMeters :+= papiReporting

        val monitoringRef = system.actorOf(Props(classOf[InfluxDBMonitoring], db, "idlemon", None))
        actors :+= monitoringRef
        val monitor = papiReporting.monitor(All)(MAX)
        monitors += hostname -> monitor
          .to(monitoringRef, subscribeHWCReport(monitor.muid, All))
          .to(monitoringRef, subscribeAggPowerReport(monitor.muid))

        Thread.sleep(config.duration * 1000)
      }

      else if (config.mode == "active") {
        implicit val timer = new JavaTimer(true)

        val influxFix = InfluxDBFactory.connect(s"http://${config.influxHost}:${config.influxPort}")
        val zkClient = ZkClient(config.zkUrl, Duration.fromSeconds(config.zkTimeout)).withAcl(OPEN_ACL_UNSAFE.asScala)

        val papiReporting = PowerMeter.loadModule(
          HWCCoreModule(None, osHelper, likwidHelper, cHelper, zkClient, influxFix, config.influxDatabase, config.influxRp.getOrElse("autogen")),
          RAPLCpuModule(likwidHelper),
          RAPLDramModule(likwidHelper)
        )
        pMeters :+= papiReporting

        val monitoringRef = system.actorOf(Props(classOf[InfluxDBMonitoring], db, "activemon", config.influxRp))
        actors :+= monitoringRef
        val reportingRef = system.actorOf(Props(classOf[InfluxDBReporting], db, "activerep" , hostname, config.influxRp))
        actors :+= reportingRef
        val monitor = papiReporting.monitor(All)(MAX)
        monitors += hostname -> monitor
          .to(monitoringRef, subscribeHWCReport(monitor.muid, All))
          .to(monitoringRef, subscribeAggPowerReport(monitor.muid))
          .to(reportingRef, subscribeAggPowerReport(monitor.muid))

        val containers = docker.listContainersCmd().exec().asScala.map(container => Container(container.getId, container.getNames.mkString(",")))
        containers.foreach(container => {
          monitors += container.id -> {
            val monitor = papiReporting.monitor(container)(MAX)
            monitor.to(reportingRef, subscribeAggPowerReport(monitor.muid))
          }
        })
        Thread.sleep(1.second.toMillis)

        while (running) {
          val currentContainers = docker.listContainersCmd().exec().asScala.map(container => Container(container.getId, container.getNames.mkString(",")))
          containers.diff(currentContainers).foreach(container => {
            monitors(container.id).cancel()
            monitors -= container.id
          })
          currentContainers.diff(containers).foreach(container => monitors += container.id -> {
            val monitor = papiReporting.monitor(container)(MAX)
            monitor.to(reportingRef, subscribeAggPowerReport(monitor.muid))
          })
          containers.clear()
          containers ++= currentContainers

          Thread.sleep(1.second.toMillis)
        }

        zkClient.release()
        timer.stop()
      }

      clockTimer.stop()

      likwidHelper.topologyFinalize()
      likwidHelper.affinityFinalize()
      likwidHelper.powerFinalize()
      likwidHelper.perfmonFinalize()

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
