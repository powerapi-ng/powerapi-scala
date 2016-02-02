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
package org.powerapi.tompecs.domain.pmodel.tegra4c

import java.util.concurrent.TimeUnit
import org.powerapi.PowerMeter
import org.powerapi.core.power._
import org.powerapi.core.target.Process
import org.powerapi.core.{Configuration, ConfigValue}
import org.powerapi.module.libpfm.{LibpfmProcessModule, LibpfmHelper}
import org.powerapi.reporter.FileDisplay
import scala.concurrent.duration.FiniteDuration
import scala.sys.process.ProcessLogger
import scala.concurrent.duration.DurationLong
import scalax.file.Path
import scala.sys.process.stringSeqToProcess
import scala.sys

/**
  * Adaptive CPU Power Models - ARM Cortex A15, 4 cores enabled (Section 5.4, Figure 11).
  */
object Experiment extends Configuration(None) with App {
  /**
    * Main configuration.
    */
  lazy val npbP = load { _.getString("powerapi.tompecs.npb-benchmarks-path") } match {
    case ConfigValue(value) => value
    case _ => ""
  }

  lazy val interval: FiniteDuration = load { _.getDuration("powerapi.tompecs.interval", TimeUnit.NANOSECONDS) } match {
    case ConfigValue(value) => value.nanoseconds
    case _ => 1l.seconds
  }

  lazy val trash = ProcessLogger(out => {}, err => {})
  lazy val separator = "="
  lazy val PSFormat = """\s*([\d]+)\s.*""".r

  val libpfmHelper = new LibpfmHelper
  libpfmHelper.init()

  @volatile var powerMeters = Seq[PowerMeter]()

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    powerMeters.foreach(powerMeter => powerMeter.shutdown())
    libpfmHelper.deinit()
  }

  val cmdBenchmark = Seq("bash", "-c", "ps -Ao pid,command") #> Seq("bash", "-c", s"grep cg.B.4") #> Seq("bash", "-c", "grep -v mpirun") #> Seq("bash", "-c", "grep -v grep")

  Path("data", '/').deleteRecursively(force = true)
  Path("/tmp/data", '/').deleteRecursively(force = true)
  Path("/tmp/data", '/').createDirectory()

  val powerapi = PowerMeter.loadModule(LibpfmProcessModule(None, libpfmHelper))
  powerMeters :+= powerapi

  val currentPid = java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split("@")(0).toInt
  var output = Array[String]()

  val p1Output = new FileDisplay("/tmp/data/output-powerapi-cg-1.dat")
  val p2Output = new FileDisplay("/tmp/data/output-powerapi-cg-2.dat")
  val p3Output = new FileDisplay("/tmp/data/output-powerapi-cg-3.dat")
  val p4Output = new FileDisplay("/tmp/data/output-powerapi-cg-4.dat")

  Seq("bash", s"scripts/workload-npb.bash", npbP, "4", "cg.B.4").!
  output = Array[String]()
  while(output.isEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }

  val pidBench1 = output(0) match {
    case PSFormat(p) => Process(p.trim.toInt)
    case _ => Process(currentPid)
  }

  val pidBench2 = output(1) match {
    case PSFormat(p) => Process(p.trim.toInt)
    case _ => Process(currentPid)
  }

  val pidBench3 = output(2) match {
    case PSFormat(p) => Process(p.trim.toInt)
    case _ => Process(currentPid)
  }

  val pidBench4 = output(3) match {
    case PSFormat(p) => Process(p.trim.toInt)
    case _ => Process(currentPid)
  }

  val wload1 = powerapi.monitor(interval)(pidBench1)(MEAN).to(p1Output)
  val wload2 = powerapi.monitor(interval)(pidBench2)(MEAN).to(p2Output)
  val wload3 = powerapi.monitor(interval)(pidBench3)(MEAN).to(p3Output)
  val wload4 = powerapi.monitor(interval)(pidBench4)(MEAN).to(p4Output)

  output = Array[String]("")
  while(output.nonEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(5l.seconds.toMillis)
  }

  wload1.cancel()
  wload2.cancel()
  wload3.cancel()
  wload4.cancel()

  Path("data", '/').createDirectory()
  (Path("/tmp/data", '/') * "*.dat").foreach(path => {
    path.moveTo(Path(s"data/${path.name}", '/'), true)
  })

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}
