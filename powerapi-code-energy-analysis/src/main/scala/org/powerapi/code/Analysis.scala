/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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
package org.powerapi.code

import java.util.UUID
import java.util.concurrent.TimeUnit
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import org.apache.logging.log4j.LogManager
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder
import org.powerapi.PowerMeter
import org.powerapi.core.target.{Target, All}
import org.powerapi.core.power._
import org.powerapi.core.{LinuxHelper, ConfigValue, Configuration}
import org.powerapi.module.libpfm.{LibpfmCoreModule, LibpfmHelper}
import org.powerapi.reporter.FileDisplay
import scala.collection.JavaConversions._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration
import scala.io.Source
import scala.sys.process.stringSeqToProcess
import scala.sys.exit
import scalax.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scalax.io.Resource
import spray.json._

case class Workload(name: String, binaryFilePath: String, script: String)

class PowerDisplay(filepath: String) extends FileDisplay(filepath) {
  override def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power): Unit = {
    output.append(s"${power.toMilliWatts}\n")
  }
}

/**
 * Main application.
 * It is responsible to monitor an intrumented program with PowerAPI, to collect the traces and then to process the traces
 * for getting the underlying call graph.
 * Once is done, the powers are gathered with the effective duration of each node in order to monitor the power consumption
 * per call during the execution.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object Analysis extends Configuration(None) with App {
  val log = LogManager.getLogger
  val formatter = new PeriodFormatterBuilder().appendHours()
    .appendSuffix("H ")
    .appendMinutes()
    .appendSuffix("m ")
    .appendSeconds()
    .appendSuffix("s ")
    .appendMillis()
    .appendSuffix("ms ")
    .toFormatter

  var start = 0l
  var stop = 0l

  val libpfmHelper = new LibpfmHelper
  val linuxHelper = new LinuxHelper

  @volatile var powerapi: PowerMeter = PowerMeter.loadModule(LibpfmCoreModule(None, libpfmHelper))

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    println("It's the time for sleeping! ...")
    powerapi.shutdown()
    libpfmHelper.deinit()
  }

  lazy val timeout: Timeout = load { _.getDuration("powerapi.actors.timeout", TimeUnit.MILLISECONDS) } match {
    case ConfigValue(value) => Timeout(value.milliseconds)
    case _ => Timeout(15l.seconds)
  }

  lazy val interval: FiniteDuration = load { _.getDuration("powerapi.code-energy-analysis.interval", TimeUnit.NANOSECONDS) } match {
    case ConfigValue(value) => value.nanoseconds
    case _ => 1l.seconds
  }

  lazy val workloads: List[Workload] = load { conf =>
    (for (item: Config <- conf.getConfigList("powerapi.code-energy-analysis.workloads"))
      yield Workload(item.getString("name"), item.getString("binary-path"), item.getString("script"))).toList
  } match {
    case ConfigValue(values) => values
    case _ => List()
  }

  libpfmHelper.init()

  val system = ActorSystem("app-system")
  val nbCores = Runtime.getRuntime.availableProcessors()

  Seq("bash", "-c", "chmod +x scripts/*").!
  Path("results", '/').deleteRecursively(force = true)
  Path("results", '/').createDirectory()

  for(workload <- workloads) {
    log.info("Start workload execution: {}", workload.name)

    start = System.currentTimeMillis
    Path(s"results/${workload.name}", '/').createDirectory()
    val display = new PowerDisplay(s"results/${workload.name}/powerapi.txt")
    val papi = powerapi.monitor(interval)(All)(MEAN).to(display)
    Seq(s"./${workload.script}", s"${workload.name}").!
    papi.cancel()
    stop = System.currentTimeMillis

    log.info("Duration: {}", formatter.print(new Period(stop - start)))

    val resolverRouter = system.actorOf(Props(classOf[AddressResolver], linuxHelper, timeout, nbCores))
    val parserRouter = system.actorOf(Props(classOf[Parser], timeout, workload.binaryFilePath, nbCores, resolverRouter))

    val traceFiles = (Path(s"results/${workload.name}", '/') * ((p: Path) => p.name != "powerapi.txt")).toSeq.map(_.toRealPath().path)

    if(traceFiles.nonEmpty) {
      log.info("Start to process traces and create call graphs, workload: {}", workload.name)

      start = System.currentTimeMillis
      val powers = Source.fromFile(s"results/${workload.name}/powerapi.txt").getLines().map(_.toDouble).toList

      val futureGraphs = Future.sequence(for(traceFilePath <- traceFiles) yield {
        parserRouter.ask(StartParsing(Source.fromFile(traceFilePath).getLines().toIterable, interval, powers))(timeout).asInstanceOf[Future[Seq[Node]]]
      })

      val graphsPerTid = Await.result(futureGraphs, timeout.duration)

      val graphPerPid = for(graphs <- graphsPerTid) yield {
        val mergedRawGraph = graphs.head

        for(rawGraph <- graphs.tail) {
          mergedRawGraph.mergeRawGraph(rawGraph)
        }

        mergedRawGraph
      }

      val mergedGraph = graphPerPid.head

      for(rawGraph <- graphPerPid.tail) {
        mergedGraph.mergeRawGraph(rawGraph)
      }

      val graph = GraphUtil.buildCallgraph(mergedGraph)

      stop = System.currentTimeMillis

      log.info("Duration: {}", formatter.print(new Period(stop - start)))
      log.info("Start to process the call graph into json, workload: {}", workload.name)

      start = System.currentTimeMillis
      Resource.fromFile(s"results/${workload.name}/${workload.name}.json").append(graph.toJson(JSONProtocol.NodeFormat).toString)
      stop = System.currentTimeMillis

      log.info("Duration: {}", formatter.print(new Period(stop - start)))
    }

    system.stop(resolverRouter)
    system.stop(parserRouter)
    system.shutdown()
    system.awaitTermination()
  }

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  exit(0)
}
