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
package org.powerapi.tompecs.plevel.xeon

import java.util.concurrent.TimeUnit
import org.powerapi.PowerMeter
import org.powerapi.core.power._
import org.powerapi.core.target.All
import org.powerapi.core.{Configuration, ConfigValue}
import org.powerapi.module.extPMeter.powerspy.PowerSpyModule
import org.powerapi.module.libpfm.{LibpfmProcessModule, LibpfmHelper}
import org.powerapi.reporter.FileDisplay
import scala.concurrent.duration.FiniteDuration
import scala.sys.process.ProcessLogger
import scala.concurrent.duration.DurationLong
import scalax.file.Path
import scala.sys.process.stringSeqToProcess
import scala.sys

/**
  * Process-level Power Monitoring - Intel Xeon W3520 (Section 5.3, Figure 10)
  */
object Experiment extends Configuration(None) with App {
  /**
    * Main configuration.
    */
  lazy val parsecP = load { _.getString("powerapi.tompecs.parsec-benchmarks-path") } match {
    case ConfigValue(value) => value
    case _ => ""
  }

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

  val cmdBenchmark1 = Seq("bash", "-c", "ps -Ao pid,command") #> Seq("bash", "-c", s"grep freqmine") #> Seq("bash", "-c", "grep -v grep")
  val cmdBenchmark2 = Seq("bash", "-c", "ps -Ao pid,command") #> Seq("bash", "-c", s"grep cg.C.2") #> Seq("bash", "-c", "grep -v mpirun") #> Seq("bash", "-c", "grep -v grep")
  val cmdBenchmark3 = Seq("bash", "-c", "ps -Ao pid,command") #> Seq("bash", "-c", s"grep bt.C.1") #> Seq("bash", "-c", "grep -v mpirun") #> Seq("bash", "-c", "grep -v grep")

  Path("data", '/').deleteRecursively(force = true)
  Path("/tmp/data", '/').deleteRecursively(force = true)
  Path("/tmp/data", '/').createDirectory()

  val powerapi = PowerMeter.loadModule(LibpfmProcessModule(None, libpfmHelper))
  powerMeters :+= powerapi
  val externalPMeter = PowerMeter.loadModule(PowerSpyModule())
  powerMeters :+= externalPMeter

  val currentPid = java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split("@")(0).toInt
  var output = Array[String]()

  val externalOutput = new FileDisplay("/tmp/data/output-external.dat")
  val powerapiOutput = new FileDisplay("/tmp/data/output-powerapi.dat")
  val p1Output = new FileDisplay("/tmp/data/output-powerapi-freqmine.dat")
  val p2Output = new FileDisplay("/tmp/data/output-powerapi-cg-1.dat")
  val p3Output = new FileDisplay("/tmp/data/output-powerapi-cg-2.dat")
  val p4Output = new FileDisplay("/tmp/data/output-powerapi-bt.dat")

  /**
    * Synchronization.
    */
  var allExPMeter = externalPMeter.monitor(interval)(All)(MEAN).to(externalOutput)
  var self = powerapi.monitor(interval)(currentPid)(MEAN).to(powerapiOutput)
  Thread.sleep(20l.seconds.toMillis)
  allExPMeter.cancel()
  self.cancel()
  Path("/tmp/data/output-external.dat", '/').delete(true)
  Path("/tmp/data/output-powerapi.dat", '/').delete(true)

  // 1
  Seq("bash", s"scripts/workload-amd64-freqmine.bash", parsecP).!
  output = Array[String]()
  while(output.isEmpty) {
    output = cmdBenchmark1.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }

  var pidBench1 = 0
  output(0) match {
    case PSFormat(p) => pidBench1 = p.trim.toInt
    case _ => {}
  }

  allExPMeter =  externalPMeter.monitor(interval)(All)(MEAN).to(externalOutput)
  self = powerapi.monitor(interval)(currentPid)(MEAN).to(powerapiOutput)
  val pid1 = powerapi.monitor(interval)(pidBench1)(MEAN).to(p1Output)
  Thread.sleep(15l.seconds.toMillis)
  (Path("/tmp/data", '/') * "*.dat").foreach(path => path.append(s"$separator\n"))

  // 2
  Seq("bash", s"scripts/workload-npb.bash", npbP, "2", "cg.C.2").!
  Array[String]()
  while(output.isEmpty) {
    output = cmdBenchmark2.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }

  var pidBench2 = 0
  output(0) match {
    case PSFormat(p) => pidBench2 = p.trim.toInt
    case _ => {}
  }
  var pidBench3 = 0
  output(1) match {
    case PSFormat(p) => pidBench3 = p.trim.toInt
    case _ => {}
  }

  val pid2 = powerapi.monitor(interval)(pidBench2)(MEAN).to(p2Output)
  val pid3 = powerapi.monitor(interval)(pidBench3)(MEAN).to(p3Output)
  Thread.sleep(15l.seconds.toMillis)
  (Path("/tmp/data", '/') * "*.dat").foreach(path => path.append(s"$separator\n"))

  // 3
  Seq("bash", s"scripts/workload-npb.bash", npbP, "1", "bt.C.1").!
  output = Array[String]()
  while(output.isEmpty) {
    output = cmdBenchmark3.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }

  var pidBench4 = 0
  output(0) match {
    case PSFormat(p) => pidBench4 = p.trim.toInt
    case _ => {}
  }

  val pid4 = powerapi.monitor(interval)(pidBench4)(MEAN).to(p4Output)
  Thread.sleep(10l.seconds.toMillis)
  (Path("/tmp/data", '/') * "*.dat").foreach(path => path.append(s"$separator\n"))

  // 4
  Seq("kill", "-9", pidBench1.toString).lineStream_!
  pid1.cancel()
  Thread.sleep(15l.seconds.toMillis)
  (Path("/tmp/data", '/') * "(.*-cg-1.dat)|(.*-cg-2.dat)|(.*-bt.dat)|(.*-external.dat)|(.*-powerapi.dat)".r).foreach(path => path.append(s"$separator\n"))

  // 5
  Seq("kill", "-9", pidBench2.toString, pidBench3.toString).lineStream_!
  pid2.cancel()
  pid3.cancel()
  Thread.sleep(10l.seconds.toMillis)
  (Path("/tmp/data", '/') * "(.*-bt.dat)|(.*-external.dat)|(.*-powerapi.dat)".r).foreach(path => path.append(s"$separator\n"))

  pid4.cancel()
  Seq("kill", "-9", pidBench4.toString).lineStream_!

  allExPMeter.cancel()
  self.cancel()

  Path("data", '/').createDirectory()
  (Path("/tmp/data", '/') * "*.dat").foreach(path => {
    path.moveTo(Path(s"data/${path.name}", '/'), true)
  })

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}
