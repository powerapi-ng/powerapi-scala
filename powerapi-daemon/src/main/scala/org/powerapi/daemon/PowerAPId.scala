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
package org.powerapi.daemon

import scala.sys.process.stringSeqToProcess

import org.apache.commons.daemon.{Daemon, DaemonContext}
import org.powerapi.core.power._
import org.powerapi.core.target.{All, Application, Container, Process, Target}
import org.powerapi.module.cpu.dvfs.CpuDvfsModule
import org.powerapi.module.cpu.simple.{ProcFSCpuSimpleModule, SigarCpuSimpleModule}
import org.powerapi.module.extpowermeter.g5komegawatt.G5kOmegaWattModule
import org.powerapi.module.extpowermeter.powerspy.PowerSpyModule
import org.powerapi.module.extpowermeter.rapl.RAPLModule
import org.powerapi.module.libpfm.{LibpfmCoreModule, LibpfmCoreProcessModule, LibpfmHelper}
import org.powerapi.reporter.{InfluxDisplay, ConsoleDisplay, FileDisplay, JFreeChartDisplay}
import org.powerapi.{PowerMeter, PowerMonitoring}

/**
  * PowerAPI daemon.
  *
  * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
  */
object PowerAPId extends App

class PowerAPId extends Daemon {

  // =====================
  // --- PowerAPI part ---

  val configuration = new DaemonConfiguration {}
  val monitor = new Thread() {
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
  @volatile var launchedPowerMeters = Seq[PowerMeter]()
  @volatile var launchedMonitors = Seq[PowerMonitoring]()
  var libpfmHelper: Option[LibpfmHelper] = None
  var stopped = false

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


  // ===================
  // --- Daemon part ---

  override def init(daemonContext: DaemonContext) {
    val args = daemonContext.getArguments
    beforeStart
  }

  def beforeStart() {
    if (System.getProperty("os.name").toLowerCase.indexOf("nix") >= 0 || System.getProperty("os.name").toLowerCase.indexOf("nux") >= 0) Seq("bash", "scripts/system.bash").!

    for ((powerModules, monitors) <- configuration.powerMeters) {
      val modules = (for (module <- powerModules) yield {
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

      for ((all, pids, apps, containers, frequency, agg, output) <- monitors) {
        val targets = {
          if (all) Seq(All)
          else Seq(pids.map(pid => Process(pid.toInt)) ++ apps.map(Application(_)) ++ containers.map(Container(_))).asInstanceOf[Seq[Target]]
        }
        val monitor = powerMeter.monitor(targets: _*)(agg).every(frequency)
        launchedMonitors :+= monitor

        output match {
          case file: String if output.startsWith("file") =>
            // file=>powerapi.out
            val fileDisplay = new FileDisplay(file.split("=>")(1))
            monitor.to(fileDisplay)
          case influx: String if output.startsWith("influx") =>
            // influx=>http://locahost:8086,powerapi,powerapi,test,event.powerapi
            val parameters = influx.split("=>")(1).split(",")
            val influxDisplay = new InfluxDisplay(parameters(0), parameters(1), parameters(2), parameters(3), parameters(4))
            monitor.to(influxDisplay)
          case "chart" =>
            val chartDisplay = new JFreeChartDisplay()
            monitor.to(chartDisplay)
          case _ =>
            val consoleDisplay = new ConsoleDisplay()
            monitor.to(consoleDisplay)
        }
      }
    }
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

  def beforeEnd() {
    launchedMonitors.foreach(monitor => monitor.cancel())
    launchedMonitors = Seq()
    launchedPowerMeters.foreach(powerMeter => powerMeter.shutdown())
    launchedPowerMeters = Seq()
  }
}

