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
package org.powerapi.daemon

import java.lang.management.ManagementFactory

import scala.concurrent.duration.{FiniteDuration, Duration, DurationInt}
import scala.sys
import scala.sys.process.stringSeqToProcess

import akka.actor.Props

import org.apache.commons.daemon.{ Daemon, DaemonContext, DaemonInitException }

import org.powerapi.core.target.{Application, All, Process, Target}
import org.powerapi.module.rapl.RAPLModule
import org.powerapi.reporter.{FileDisplay, JFreeChartDisplay, ConsoleDisplay}
import org.powerapi.{PowerMonitoring, PowerMeter}
import org.powerapi.core.power._
import org.powerapi.module.cpu.dvfs.CpuDvfsModule
import org.powerapi.module.cpu.simple.{SigarCpuSimpleModule, ProcFSCpuSimpleModule}
import org.powerapi.module.libpfm.{LibpfmHelper, LibpfmCoreProcessModule, LibpfmCoreModule}
import org.powerapi.module.extPMeter.powerspy.PowerSpyModule
import org.powerapi.module.extPMeter.g5k.G5kOmegaWattModule

/**
 * PowerAPI daemon.
 *
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object PowerAPId extends App
class PowerAPId extends Daemon {

  // =====================
  // --- PowerAPI part ---

  val configuration = new DaemonConfiguration {}
  
  val pidR = """(\d+)""".r
  val appR = """(.+)""".r

  @volatile var launchedPowerMeters = Seq[PowerMeter]()
  @volatile var launchedMonitors = Seq[PowerMonitoring]()
  
  var libpfmHelper: Option[LibpfmHelper] = None
  

  implicit def aggStrToAggFunction(str: String): Seq[Power] => Power = {
    str match {
      case "max" => MAX
      case "min" => MIN
      case "geomean" => GEOMEAN
      case "logsum" => LOGSUM
      case "mean" => MEAN
      case "median" => MEDIAN
      case "stdev" => STDEV
      case "variance" => VARIANCE
      case _ => SUM
    }
  }
  
  implicit def targetsStrToTargets(targets: Set[String]): Seq[Target] = {
    if(targets.contains("all")) {
      Seq(All)
    }

    (for(target <- targets) yield {
      target match {
        case "all" => All
        case pidR(pid) => Process(pid.toInt)
        case appR(app) => Application(app)
        case _ => Process(ManagementFactory.getRuntimeMXBean.getName.split("@")(0).toInt)
      }
    }).toSeq
  }
  
  def beforeStart() {
    if(System.getProperty("os.name").toLowerCase.indexOf("nix") >= 0 || System.getProperty("os.name").toLowerCase.indexOf("nux") >= 0) Seq("bash", "scripts/system.bash").!
  
    for ((powerModules, monitors) <- configuration.powerMeters) {
      val modules = (for(module <- powerModules) yield {
        module match {
          case "procfs-cpu-simple" => ProcFSCpuSimpleModule()
          case "sigar-cpu-simple" => SigarCpuSimpleModule()
          case "cpu-dvfs" => CpuDvfsModule()
          case "libpfm-core" => {
            libpfmHelper = Some(new LibpfmHelper)
            libpfmHelper.get.init()
            LibpfmCoreModule(None, libpfmHelper.get)
          }
          case "libpfm-core-process" => {
            libpfmHelper = Some(new LibpfmHelper)
            libpfmHelper.get.init()
            LibpfmCoreProcessModule(None, libpfmHelper.get)
          }
          case "powerspy" => PowerSpyModule(None)
          case "g5k-omegawatt" => G5kOmegaWattModule(None)
          case "rapl" => RAPLModule()
        }
      }).toSeq
      
      val powerMeter = PowerMeter.loadModule(modules: _*)
      launchedPowerMeters :+= powerMeter
      
      for ((targets, frequency, agg, output) <- monitors) {
        val monitor = powerMeter.monitor(frequency)(targets: _*)(agg)
        launchedMonitors :+= monitor
      
        output match {
          case file:String if output.startsWith("file") => {
            val fileDisplay = new FileDisplay(file.split(":")(1))
            monitor.to(fileDisplay)
          }
          case "chart" => {
            val chartDisplay = new JFreeChartDisplay()
            monitor.to(chartDisplay)
          }
          case _ => {
            val consoleDisplay = new ConsoleDisplay()
            monitor.to(consoleDisplay)
          }
        }
      }
    }
  }
  def beforeEnd() {
    launchedMonitors.foreach(monitor => monitor.cancel())
    launchedMonitors = Seq()
    launchedPowerMeters.foreach(powerMeter => powerMeter.shutdown())
    launchedPowerMeters = Seq()
  }


  // ===================
  // --- Daemon part ---

  var stopped = false
  val monitor = new Thread(){
    override def start() {
      this.synchronized {
        PowerAPId.this.stopped = false
        super.start
      }
    }
    override def run() {
      while (!stopped) Thread.sleep(2000)
    }
  }
  
  override def init(daemonContext: DaemonContext) {
    val args = daemonContext.getArguments
    beforeStart
  }
  
  override def start() {
    monitor.start
  }
  
  override def stop() {
    stopped = true
    try {
      monitor.join(1000)
    } catch {
      case e: InterruptedException => {
        System.err.println(e.getMessage)
        throw e
      }
    }
  }
  
  override def destroy() {
    beforeEnd
  }
}

