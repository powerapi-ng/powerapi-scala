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
package org.powerapi.jss.system.impact.xeon

import java.util.concurrent.TimeUnit
import org.powerapi.PowerMeter
import org.powerapi.core.power._
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
import scalax.io.Resource

/**
  * System Impact On CPU Power Models - Intel Xeon W3520 Ubuntu (default), CentOS (balanced, latency-performance) (Section 5.5, Figure 12)
  * Don't forget to change the configuration file to use in app.conf.
  */
object Experiment extends Configuration(None) with App {
  /**
    * Main configuration.
    */
  lazy val npbP = load { _.getString("powerapi.jss.npb-benchmarks-path") } match {
    case ConfigValue(value) => value
    case _ => ""
  }

  lazy val interval: FiniteDuration = load { _.getDuration("powerapi.jss.interval", TimeUnit.NANOSECONDS) } match {
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

  val cmdBenchmark = Seq("bash", "-c", "ps -Ao pid,command") #> Seq("bash", "-c", s"grep bt.B.1") #> Seq("bash", "-c", "grep -v mpirun") #> Seq("bash", "-c", "grep -v grep")

  Path("data", '/').deleteRecursively(force = true)
  Path("/tmp/data", '/').deleteRecursively(force = true)
  Path("/tmp/data", '/').createDirectory()

  val powerapi = PowerMeter.loadModule(LibpfmProcessModule(None, libpfmHelper))
  powerMeters :+= powerapi
  val externalPMeter = PowerMeter.loadModule(PowerSpyModule())
  powerMeters :+= externalPMeter

  val currentPid = java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split("@")(0).toInt
  var output = Array[String]()

  val pOutput = new FileDisplay("/tmp/data/output-powerapi-bt.dat")

  var begin = System.currentTimeMillis()
  Seq("bash", s"scripts/workload-npb.bash", npbP, "1", "bt.B.1").!
  output = Array[String]()
  while(output.isEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }

  var pidBench1 = 0
  output(0) match {
    case PSFormat(p) => pidBench1 = p.trim.toInt
    case _ => {}
  }

  Seq("taskset", "-cp", "0", s"$pidBench1").!(trash)
  Seq("cpulimit", "-l", "75", "-p", s"$pidBench1").run(trash)

  val pid1 = powerapi.monitor(interval)(pidBench1)(MEAN).to(pOutput)
  output = Array[String]("")
  while(output.nonEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }
  var end = System.currentTimeMillis()
  Resource.fromFile("/tmp/data/timing.dat").append(s"${end-begin}\n")
  (Path("/tmp/data", '/') * "*.dat").foreach(path => path.append(s"$separator\n"))
  pid1.cancel()


  begin = System.currentTimeMillis()
  Seq("bash", s"scripts/workload-npb.bash", npbP, "1", "bt.B.1").!
  output = Array[String]()
  while(output.isEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }

  var pidBench2 = 0
  output(0) match {
    case PSFormat(p) => pidBench2 = p.trim.toInt
    case _ => {}
  }

  Seq("taskset", "-cp", "0", s"$pidBench2").!(trash)
  Seq("cpulimit", "-l", "25", "-p", s"$pidBench2").run(trash)

  val pid2 = powerapi.monitor(interval)(pidBench2)(MEAN).to(pOutput)
  output = Array[String]("")
  while(output.nonEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }
  end = System.currentTimeMillis()
  Resource.fromFile("/tmp/data/timing.dat").append(s"${end-begin}\n")
  (Path("/tmp/data", '/') * "*.dat").foreach(path => path.append(s"$separator\n"))
  pid2.cancel()


  begin = System.currentTimeMillis()
  Seq("bash", s"scripts/workload-npb.bash", npbP, "1", "bt.B.1").!
  output = Array[String]()
  while(output.isEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }

  var pidBench3 = 0
  output(0) match {
    case PSFormat(p) => pidBench3 = p.trim.toInt
    case _ => {}
  }

  Seq("taskset", "-cp", "0", s"$pidBench3").!(trash)

  val pid3 = powerapi.monitor(interval)(pidBench3)(MEAN).to(pOutput)
  output = Array[String]("")
  while(output.nonEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }
  end = System.currentTimeMillis()
  Resource.fromFile("/tmp/data/timing.dat").append(s"${end-begin}\n")
  (Path("/tmp/data", '/') * "*.dat").foreach(path => path.append(s"$separator\n"))
  pid3.cancel()


  begin = System.currentTimeMillis()
  Seq("bash", s"scripts/workload-npb.bash", npbP, "1", "bt.B.1").!
  Seq("bash", s"scripts/workload-npb.bash", npbP, "1", "bt.B.1").!
  Seq("bash", s"scripts/workload-npb.bash", npbP, "1", "bt.B.1").!
  Seq("bash", s"scripts/workload-npb.bash", npbP, "1", "bt.B.1").!

  output = Array[String]()
  while(output.length < 4) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }

  var pidBench4 = 0
  output(0) match {
    case PSFormat(p) => pidBench4 = p.trim.toInt
    case _ => {}
  }

  var pidBench5 = 0
  output(1) match {
    case PSFormat(p) => pidBench5 = p.trim.toInt
    case _ => {}
  }

  var pidBench6 = 0
  output(2) match {
    case PSFormat(p) => pidBench6 = p.trim.toInt
    case _ => {}
  }

  var pidBench7 = 0
  output(3) match {
    case PSFormat(p) => pidBench7 = p.trim.toInt
    case _ => {}
  }

  Seq("taskset", "-cp", "0", s"$pidBench4").!(trash)
  Seq("taskset", "-cp", "1", s"$pidBench5").!(trash)
  Seq("taskset", "-cp", "2", s"$pidBench6").!(trash)
  Seq("taskset", "-cp", "3", s"$pidBench7").!(trash)

  val pidAll = powerapi.monitor(interval)(pidBench4, pidBench5, pidBench6, pidBench7)(SUM).to(pOutput)
  output = Array[String]("")
  while(output.nonEmpty) {
    output = cmdBenchmark.lineStream_!.toArray
    Thread.sleep(1l.seconds.toMillis)
  }
  end = System.currentTimeMillis()
  Resource.fromFile("/tmp/data/timing.dat").append(s"${end-begin}\n")
  (Path("/tmp/data", '/') * "*.dat").foreach(path => path.append(s"$separator\n"))
  pidAll.cancel()

  Path("data", '/').createDirectory()
  (Path("/tmp/data", '/') * "*.dat").foreach(path => {
    path.moveTo(Path(s"data/${path.name}", '/'), true)
  })

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}
