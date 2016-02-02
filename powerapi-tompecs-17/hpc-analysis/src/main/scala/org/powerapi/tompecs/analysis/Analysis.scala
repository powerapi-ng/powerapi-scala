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
package org.powerapi.tompecs.analysis

import java.io.{PrintWriter, FileOutputStream, File}
import java.util.concurrent.TimeUnit
import akka.actor.{Props, ActorSystem, ActorLogging, Actor}
import com.typesafe.config.Config
import org.apache.logging.log4j.LogManager
import org.apache.commons.math.stat.correlation.PearsonsCorrelation
import org.powerapi.PowerMeter
import org.powerapi.core.target.All
import org.powerapi.core.power.MEAN
import org.powerapi.core.{ConfigValue, Configuration}
import org.powerapi.module.PowerChannel.AggregatePowerReport
import org.powerapi.module.extPMeter.powerspy.PowerSpyModule
import org.powerapi.module.libpfm.{Event, PMU, LibpfmHelper, LibpfmCoreSensorModule}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{PCReport, subscribePCReport}
import org.saddle.Vec
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.sys.process.ProcessLogger
import scalax.file.Path
import scalax.file.PathMatcher.IsDirectory
import scala.collection.JavaConversions._
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process.stringSeqToProcess
import scala.sys

object RENEW

/**
  * Define specific kinds of reporters to be sure that all data are written inside files.
  */
class PowersDisplay(filepath: String) extends org.powerapi.core.APIComponent {
  var output = new PrintWriter(new FileOutputStream(new File(filepath), true))

  override def postStop(): Unit = {
    output.close()
    super.postStop()
  }

  def receive: Actor.Receive = {
    case msg: AggregatePowerReport => report(msg)
    case msg: String => append(msg)
    case RENEW => {
      output.close()
      Path(s"$filepath", '/').delete(true)
      output = new PrintWriter(new FileOutputStream(new File(filepath), true))
    }
  }

  def report(msg: AggregatePowerReport): Unit = {
    output.append(s"${msg.power.toWatts}\n")
    output.flush()
  }

  def append(msg: String): Unit = {
    output.append(s"$msg\n")
    output.flush()
  }
}

class CountersDisplay(mapping: Map[String, String], events: List[String]) extends Actor with ActorLogging  {
  var outputs = (for(event <- events) yield {
    event -> new PrintWriter(new FileOutputStream(new File(mapping(event)), true))
  }).toMap

  override def postStop(): Unit = {
    outputs.foreach {
      case (_, writer) => writer.close()
    }
    super.postStop()
  }

  def receive: Actor.Receive = {
    case msg: PCReport => report(msg)
    case msg: String => append(msg)
    case RENEW => {
      outputs.foreach {
        case (event, writer) => {
          writer.close()
          Path(mapping(event), '/').delete(true)
        }
      }

      outputs = (for(event <- events) yield {
        event -> new PrintWriter(new FileOutputStream(new File(mapping(event)), true))
      }).toMap
    }
  }

  def report(msg: PCReport): Unit = {
    for((event, wrappers) <- msg.wrappers.groupBy(_.event)) {
      val future = Future.sequence(wrappers.foldLeft(List[Future[Long]]())((acc, elt) => acc ++ elt.values))

      future onSuccess {
        case values: List[Long] => {
          val counter = values.foldLeft(0l)((acc, value) => acc + value)
          outputs(event).append(s"$counter\n")
          outputs(event).flush()
        }
      }

      future onFailure {
        case ex: Throwable => {
          log.warning("An error occurred: {}", ex.getMessage)
        }
      }
    }
  }

  def append(msg: String): Unit = {
    outputs.values.foreach(output => {
      output.append(s"$msg\n")
      output.flush()
    })
  }
}

case class Workload(path: String, benchmarks: List[String], scriptPhase1: String, scriptPhase2: String)

/**
  * HPC events analysis.
  */
object Analysis extends Configuration(None) {

  def apply(): Unit = {
    @volatile var powerapi: Option[PowerMeter] = None
    @volatile var externalPMeter: Option[PowerMeter] = None

    val libpfmHelper = new LibpfmHelper

    val shutdownHookThread = scala.sys.ShutdownHookThread {
      println("It's the time for sleeping! ...")
      powerapi match {
        case Some(papi) => papi.shutdown()
        case _ => {}

      }
      externalPMeter match {
        case Some(exPMeter) => exPMeter.shutdown()
        case _ => {}
      }
      libpfmHelper.deinit()
    }

    lazy val interval: FiniteDuration = load {
      _.getDuration("powerapi.hpc-analysis.interval", TimeUnit.NANOSECONDS)
    } match {
      case ConfigValue(value) => value.nanoseconds
      case _ => 1.seconds
    }

    lazy val nbRuns: Int = load {
      _.getInt("powerapi.hpc-analysis.nb-runs")
    } match {
      case ConfigValue(value) => value
      case _ => 1
    }

    lazy val workloads: List[Workload] = load { conf =>
      (for (item: Config <- conf.getConfigList("powerapi.hpc-analysis.workloads"))
        yield Workload(item.getString("path"), item.getStringList("benchmarks").toList, item.getString("script-phase1"), item.getString("script-phase2"))).toList
    } match {
      case ConfigValue(values) => values
      case _ => List()
    }

    libpfmHelper.init()

    Seq("bash", "-c", "chmod +x scripts/*").!
    Seq("./scripts/build.bash").!

    val trash = ProcessLogger(out => {}, err => {})
    val log = LogManager.getLogger
    val PSFormat = """\s*([\d]+)\s.*""".r

    val begin = System.nanoTime

    // Computes the different combinations with the events associated to each PMU.
    var pmus = libpfmHelper.availablePMUS()
    if (pmus.size == 0) {
      log.error("No PMU detected on this processor.")
      sys.exit(0)
    }
   /* var events = pmus(0).events.sliding(pmus(0).nbGenericCounters, pmus(0).nbGenericCounters).toList
    for (pmu <- pmus.tail) {
      events = for ((l1, l2) <- events.zipAll(pmu.events.sliding(pmu.nbGenericCounters, pmu.nbGenericCounters).toList, List(), List())) yield l1 ++ l2
    }*/

    // Test
    val events = List(List(Event("hsw_ep", "CPU_CLK_UNHALTED:THREAD_P", ""), Event("hsw_ep", "CPU_CLK_UNHALTED:REF_P", ""), Event("hsw_ep", "OFFCORE_RESPONSE_0:snp_non_dram", ""), Event("hsw_ep", "baclears:any", "")))

    val separator = "="
    val outputPowers = "output-powers.dat"

    val eventsMapping = (for (event <- events.flatten) yield s"${event.pmu}::${event.name}" -> s"output-${event.pmu}--${event.name.toLowerCase.replace(':', '-')}.dat").toMap

    val pearsonUtil = new PearsonsCorrelation()
    var coeffsPhase1 = Map[String, Vec[Double]]()
    var hpcsWhitelistPhase1 = Map[String, Double]()
    var hpcsBlacklistPhase1 = List[String]()

    (Path(".") * "*.log").foreach(path => path.delete(force = true))
    (Path(".") * "*.dat").foreach(path => path.delete(force = true))

    /**
      * Special actor system and writers.
      */
    val writersSys = ActorSystem("writers")

    /**
      * We keep the connexion opened.
      */
    externalPMeter = Some(PowerMeter.loadModule(PowerSpyModule()))
    val externalPMeterDisplay = writersSys.actorOf(Props(classOf[PowersDisplay], s"$outputPowers"), "powers")
    val allExPMeter = externalPMeter.get.monitor(interval)(All)(MEAN).to(externalPMeterDisplay)
    Thread.sleep(30.seconds.toMillis)
    allExPMeter.cancel()
    writersSys.stop(externalPMeterDisplay)
    Thread.sleep(5.seconds.toMillis)

    (Path(".") * "*.dat").foreach(path => path.delete(force = true))

    /**
      * Phase 1 - Remove several counters that are not relevant
      */
    Path("phase1", '/').deleteRecursively(force = true)

    log.info("Phase 1")

    for (workload <- workloads) {
      for (benchmark <- workload.benchmarks) {
        for (run <- 1 to nbRuns) {
          log.info("benchmark: {}, run: {}", benchmark, s"$run")

          for ((subset, index) <- events.zipWithIndex) {
            powerapi = Some(PowerMeter.loadModule(LibpfmCoreSensorModule(None, libpfmHelper, (for (event <- subset) yield s"${event.pmu}::${event.name}").toSet)))
            val powerapiDisplay = writersSys.actorOf(Props(classOf[CountersDisplay], eventsMapping, for (event <- subset) yield s"${event.pmu}::${event.name}"), "output-cpu")
            val externalPMeterDisplay = writersSys.actorOf(Props(classOf[PowersDisplay], s"$outputPowers"), "output-powers")

            var allExPMeter = externalPMeter.get.monitor(interval)(All)(MEAN).to(externalPMeterDisplay)
            var allPapi = powerapi.get.monitor(interval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)
            Thread.sleep(30.seconds.toMillis)
            allExPMeter.cancel()
            allPapi.cancel()
            Thread.sleep(10.seconds.toMillis)

            allExPMeter = externalPMeter.get.monitor(interval)(All)(MEAN).to(externalPMeterDisplay)
            allPapi = powerapi.get.monitor(interval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)
            Seq(s"./${workload.scriptPhase1}", s"${workload.path}", benchmark).!
            allExPMeter.cancel()
            allPapi.cancel()

            writersSys.stop(externalPMeterDisplay)
            writersSys.stop(powerapiDisplay)
            powerapi.get.shutdown()
            powerapi = None

            for (pmu <- pmus) Path(s"phase1/$benchmark/run-$run/subset-$index/${pmu.name}", '/').createDirectory()
            (Path(".", '/') * ((p: Path) => !p.name.equals(outputPowers) && p.name.endsWith(".dat"))).foreach(path => {
              val pmu = path.name.substring(0, path.name.indexOf("--")).replace("output-", "")
              path.moveTo(Path(s"phase1/$benchmark/run-$run/subset-$index/$pmu/${path.name}", '/'), true)
            })
            Path(outputPowers, '/').moveTo(Path(s"phase1/$benchmark/run-$run/subset-$index/$outputPowers", '/'), true)

            Thread.sleep(10.seconds.toMillis)
          }
        }
      }
    }

/*    /**
      * Remove a counter when the pearson coefficient is smaller than a threshold.
      */
    for (benchmarkPath <- Path(s"phase1", '/') ** IsDirectory) {
      for (runPath <- benchmarkPath ** IsDirectory) {
        for (subsetPath <- runPath ** IsDirectory) {
          for (pmuPath <- subsetPath ** IsDirectory) {
            val eventFilepaths = (pmuPath * "*.dat").filter(_.name != outputPowers)
            val powersData = (subsetPath / outputPowers).lines().filter(_ != "\n").map(_.toDouble)

            for (eventFilepath <- eventFilepaths) {
              val event = eventsMapping.filter(_._2 == eventFilepath.name).head._1
              val eventData = eventFilepath.lines().filter(_ != "\n").map(_.toDouble)
              val minSize = math.min(powersData.size, eventData.size)
              val pearson = math.abs(pearsonUtil.correlation(eventData.slice(0, minSize).toArray, powersData.slice(0, minSize).toArray))
              if (pearson.isNaN) {
                coeffsPhase1 += event -> coeffsPhase1.getOrElse(event, Vec[Double]()).concat(Vec(0))
              }
              else coeffsPhase1 += event -> coeffsPhase1.getOrElse(event, Vec[Double]()).concat(Vec(pearson))
            }
          }
        }
      }
    }

    for ((event, coeffs) <- coeffsPhase1) {
      val median = coeffs.median

      if (median > 0.5) {
        hpcsWhitelistPhase1 += event -> median
      }
      else hpcsBlacklistPhase1 :+= event
    }


    pmus = for (pmu <- pmus) yield {
      PMU(pmu.name, pmu.nbGenericCounters, pmu.events.filterNot(event => hpcsBlacklistPhase1.filter(_.contains(s"${pmu.name}::")).contains(s"${event.pmu}::${event.name}")))
    }

    events = pmus(0).events.sliding(pmus(0).nbGenericCounters, pmus(0).nbGenericCounters).toList
    for (pmu <- pmus.tail) {
      events = for ((l1, l2) <- events.zipAll(pmu.events.sliding(pmu.nbGenericCounters, pmu.nbGenericCounters).toList, List(), List())) yield l1 ++ l2
    }

    hpcsWhitelistPhase1 = hpcsWhitelistPhase1.toSeq.sortWith(_._2 > _._2).toMap
    log.info("Number of selected counters in the first phase: {}", s"${hpcsWhitelistPhase1.size}")
    log.info("Number of removed counters in the first phase: {}", s"${hpcsBlacklistPhase1.size}")
    log.info("Removed counters after the first phase: {}", hpcsBlacklistPhase1.mkString(","))
    log.info("Remaining counters after the first phase: {}", hpcsWhitelistPhase1.mkString(","))
    log.info("*******")

    /**
      * Phase 2 - Try to delete more counters with complete runs.
      */
    (Path(".") * "*.dat").foreach(path => path.delete(force = true))
    Path("phase2", '/').deleteRecursively(force = true)
    log.info("Phase 2")

    for (workload <- workloads) {
      for (benchmark <- workload.benchmarks) {
        for (run <- 1 to nbRuns) {
          log.info("benchmark: {}, run: {}", benchmark, s"$run")

          for ((subset, index) <- events.zipWithIndex) {
            powerapi = Some(PowerMeter.loadModule(LibpfmCoreSensorModule(None, libpfmHelper, (for (event <- subset) yield s"${event.pmu}::${event.name}").toSet)))
            val powerapiDisplay = writersSys.actorOf(Props(classOf[CountersDisplay], eventsMapping, for (event <- subset) yield s"${event.pmu}::${event.name}"), "output-cpu")
            val externalPMeterDisplay = writersSys.actorOf(Props(classOf[PowersDisplay], s"$outputPowers"), "output-powers")

            var allExPMeter = externalPMeter.get.monitor(interval)(All)(MEAN).to(externalPMeterDisplay)
            var allPapi = powerapi.get.monitor(interval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)
            Thread.sleep(20.seconds.toMillis)
            allExPMeter.cancel()
            allPapi.cancel()
            Thread.sleep(10.seconds.toMillis)

            allExPMeter = externalPMeter.get.monitor(interval)(All)(MEAN).to(externalPMeterDisplay)
            allPapi = powerapi.get.monitor(interval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)
            Seq(s"./${workload.scriptPhase2}", s"${workload.path}", benchmark).!
            allExPMeter.cancel()
            allPapi.cancel()

            writersSys.stop(externalPMeterDisplay)
            writersSys.stop(powerapiDisplay)
            powerapi.get.shutdown()
            powerapi = None

            for (pmu <- pmus) Path(s"phase2/$benchmark/run-$run/subset-$index/${pmu.name}", '/').createDirectory()
            (Path(".", '/') * ((p: Path) => !p.name.equals(outputPowers) && p.name.endsWith(".dat"))).foreach(path => {
              val pmu = path.name.substring(0, path.name.indexOf("--")).replace("output-", "")
              path.moveTo(Path(s"phase2/$benchmark/run-$run/subset-$index/$pmu/${path.name}", '/'), true)
            })
            Path(outputPowers, '/').moveTo(Path(s"phase2/$benchmark/run-$run/subset-$index/$outputPowers", '/'), true)

            Thread.sleep(10.seconds.toMillis)
          }
        }
      }
    }*/

    log.info("Analysis over")

    shutdownHookThread.start()
    shutdownHookThread.join()
    shutdownHookThread.remove()
  }
}
