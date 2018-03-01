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
package org.powerapi.app

import java.lang.management.ManagementFactory

import com.spotify.docker.client.exceptions.ContainerNotFoundException
import com.spotify.docker.client.messages.ContainerInfo
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.sys
import scala.sys.process.stringSeqToProcess
import scala.util.matching.Regex
import org.powerapi.core.power._
import org.powerapi.core.target._
import org.powerapi.module.cpu.dvfs.CpuDvfsModule
import org.powerapi.module.cpu.simple.{ProcFSCpuSimpleModule, SigarCpuSimpleModule}
import org.powerapi.module.disk.simple.DiskSimpleModule
import org.powerapi.module.extpowermeter.g5komegawatt.G5kOmegaWattModule
import org.powerapi.module.extpowermeter.powerspy.PowerSpyModule
import org.powerapi.module.extpowermeter.rapl.RAPLModule
import org.powerapi.module.libpfm.{LibpfmCoreModule, LibpfmCoreProcessModule, LibpfmHelper, LibpfmModule, LibpfmProcessModule}
import org.powerapi.reporter.{ConsoleDisplay, FileDisplay, InfluxDisplay, JFreeChartDisplay}
import org.powerapi.{PowerDisplay, PowerMeter, PowerMonitoring}

/**
  * PowerAPI CLI.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
  */
object PowerAPI extends App {
  val modulesR = """(procfs-cpu-simple|sigar-cpu-simple|cpu-dvfs|libpfm|libpfm-process|libpfm-core|libpfm-core-process|powerspy|g5k-omegawatt|rapl|disk-simple)(,(procfs-cpu-simple|sigar-cpu-simple|cpu-dvfs|libpfm|libpfm-process|libpfm-core|libpfm-core-process|powerspy|g5k-omegawatt|rapl|disk-simple))*""".r
  val aggR = """max|min|mean|median|sum""".r
  val durationR = """\d+""".r
  val pidsR = """(\d+)(,(\d+))*""".r
  val appsR = """([^,]+)(,([^,]+))*""".r
  val containersR = """([^,]+)(,([^,]+))*""".r

  @volatile var powerMeters = Seq[PowerMeter]()
  @volatile var monitors = Seq[PowerMonitoring]()

  val docker: DockerClient = new DefaultDockerClient("unix:///var/run/docker.sock")

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    println("PowerAPI is shutting down ...")
    monitors.foreach(monitor => monitor.cancel())
    monitors = Seq()
    powerMeters.foreach(powerMeter => powerMeter.shutdown())
    powerMeters = Seq()
  }

  def validate(regex: Regex, str: String): Boolean = str match {
    case regex(_*) => true
    case _ => false
  }

  implicit def aggStrToAggFunction(str: String): Seq[Power] => Power = {
    str match {
      case "max" => MAX
      case "min" => MIN
      case "mean" => MEAN
      case "median" => MEDIAN
      case "sum" => SUM
    }
  }

  def printHelp(): Unit = {
    val str =
      """
        |PowerAPI, Spirals Team
        |
        |Build a software-defined power meter. Do not forget to configure correctly the modules.
        |Different settings can be used per software-defined power meter by using the prefix option.
        |Please, refer to the documentation inside the GitHub wiki for further details.
        |
        |usage: ./powerapi modules procfs-cpu-simple|sigar-cpu-simple|cpu-dvfs|libpfm|libpfm-process|libpfm-core|libpfm-core-process|powerspy|g5k-omegawatt|rapl|disk-simple (1, *) *--prefix [name]*
        |                          monitor (1, *)
        |                            --frequency $MILLISECONDS
        |                            --self (0, 1) --pids [pid, ...] (0, *) --apps [app, ...] (0, *) --containers [name or ID, ...] (0, *) | all (0, 1)
        |                            --agg max|min|mean|median|sum
        |                            --console (0, 1) --file $FILEPATH (0, *) --chart (0, 1) --influx $HOST $PORT $USER $PWD $DB $MEASUREMENT (0, *)
        |                  duration [s]
        |
        |example: ./powerapi modules procfs-cpu-simple monitor --frequency 1000 --apps firefox,chrome --agg max --console \
        |                    modules powerspy --prefix powermeter2 monitor --frequency 1000 --all --agg max --console \
        |                    duration 30
      """.stripMargin

    println(str)
  }

  def cli(options: List[Map[Symbol, Any]], duration: String, args: List[String]): (List[Map[Symbol, Any]], String) = args match {
    case Nil =>
      (options, duration)
    case "modules" :: value :: "--prefix" :: prefix :: "monitor" :: tail if validate(modulesR, value) =>
      val (remainingArgs, monitors) = cliMonitorsSubcommand(List(), Map(), tail.map(_.toString))
      cli(options :+ Map('modules -> value, 'prefix -> Some(prefix), 'monitors -> monitors), duration, remainingArgs)
    case "modules" :: value :: "monitor" :: tail if validate(modulesR, value) =>
      val (remainingArgs, monitors) = cliMonitorsSubcommand(List(), Map(), tail.map(_.toString))
      cli(options :+ Map('modules -> value, 'prefix -> None, 'monitors -> monitors), duration, remainingArgs)
    case "duration" :: value :: tail if validate(durationR, value) =>
      cli(options, value, tail)
    case option :: tail =>
      println(s"unknown cli option $option")
      sys.exit(1)
  }

  def cliMonitorsSubcommand(options: List[Map[Symbol, Any]], currentMonitor: Map[Symbol, Any],
                            args: List[String]): (List[String], List[Map[Symbol, Any]]) = args match {
    case Nil =>
      (List(), options :+ currentMonitor)
    case "modules" :: value :: "--prefix" :: prefix :: "monitor" :: tail if validate(modulesR, value) =>
      (List("modules", value, "--prefix", prefix, "monitor") ++ tail, options :+ currentMonitor)
    case "modules" :: value :: "monitor" :: tail if validate(modulesR, value) =>
      (List("modules", value, "monitor") ++ tail, options :+ currentMonitor)
    case "duration" :: value :: tail if validate(durationR, value) =>
      (List("duration", value) ++ tail, options :+ currentMonitor)
    case "monitor" :: tail =>
      cliMonitorsSubcommand(options :+ currentMonitor, Map(), tail)
    case "--frequency" :: value :: tail if validate(durationR, value) =>
      cliMonitorsSubcommand(options, currentMonitor ++ Map('frequency -> value), tail)
    case "--self" :: tail =>
      cliMonitorsSubcommand(options,
        currentMonitor + ('targets ->
          (currentMonitor.getOrElse('targets, Set[Any]()).asInstanceOf[Set[Any]] + Process(ManagementFactory.getRuntimeMXBean.getName.split("@")(0).toInt))
      ), tail)
    case "--pids" :: value :: tail if validate(pidsR, value) =>
      cliMonitorsSubcommand(options,
        currentMonitor + ('targets ->
          (currentMonitor.getOrElse('targets, Set[Any]()).asInstanceOf[Set[Any]] ++ value.split(",").map(pid => Process(pid.toInt)))
      ), tail)
    case "--apps" :: value :: tail if validate(appsR, value) =>
      cliMonitorsSubcommand(options, currentMonitor + ('targets ->
        (currentMonitor.getOrElse('targets, Set[Any]()).asInstanceOf[Set[Any]] ++ value.split(",").map(app => Application(app)))
      ), tail)
    case "--containers" :: value :: tail if validate(containersR, value) =>
      cliMonitorsSubcommand(options, currentMonitor + ('targets ->
        (currentMonitor.getOrElse('targets, Set[Any]()).asInstanceOf[Set[Any]] ++ value.split(",").flatMap {
          container =>
            try {
              val targetInformation: ContainerInfo = docker.inspectContainer(container)
              Some(Container(targetInformation.id(), targetInformation.name()))
            }
            catch {
              case _: ContainerNotFoundException =>
                println("Container '" + container + "' does not exist")
                sys.exit(1)
                None
            }
        })
      ), tail)
    case "--all" :: tail =>
      cliMonitorsSubcommand(options, currentMonitor + ('targets ->
        (currentMonitor.getOrElse('targets, Set[Any]()).asInstanceOf[Set[Any]] + All)
      ), tail)
    case "--agg" :: value :: tail if validate(aggR, value) =>
      cliMonitorsSubcommand(options, currentMonitor ++ Map('agg -> value), tail)
    case "--console" :: tail =>
      cliMonitorsSubcommand(options, currentMonitor + ('displays ->
        (currentMonitor.getOrElse('displays, Set[Any]()).asInstanceOf[Set[Any]] + new ConsoleDisplay)
      ), tail)
    case "--file" :: value :: tail =>
      cliMonitorsSubcommand(options, currentMonitor + ('displays ->
        (currentMonitor.getOrElse('displays, Set[Any]()).asInstanceOf[Set[Any]] + new FileDisplay(value))
      ), tail)
    case "--chart" :: tail =>
      cliMonitorsSubcommand(options, currentMonitor + ('displays ->
        (currentMonitor.getOrElse('displays, Set[Any]()).asInstanceOf[Set[Any]] + new JFreeChartDisplay)
      ), tail)
    case "--influx" :: host :: port :: user :: pwd :: db :: measurement :: tail =>
      cliMonitorsSubcommand(options, currentMonitor + ('displays -> {
        val influxDisplay = new InfluxDisplay(host, port.toInt, user, pwd, db, measurement)
        Await.result(influxDisplay.database.create(), 30.seconds)
        currentMonitor.getOrElse('displays, Set[Any]()).asInstanceOf[Set[Any]] + influxDisplay
      }), tail)
    case option :: tail =>
      println(s"unknown monitor option $option")
      sys.exit(1)
  }

  if (args.isEmpty) {
    printHelp()
    sys.exit(1)
  }

  else {
    if (System.getProperty("os.name").toLowerCase.indexOf("nix") >= 0 || System.getProperty("os.name").toLowerCase.indexOf("nux") >= 0) {
      Seq("bash", "scripts/system.bash").!
    }
    val (configuration, duration) = cli(List(), "3600", args.toList)

    var libpfmHelper: Option[LibpfmHelper] = None

    if (configuration.count(powerMeterConf => powerMeterConf('modules).toString.contains("libpfm")) != 0) {
      libpfmHelper = Some(new LibpfmHelper)
      libpfmHelper.get.init()
    }

    for (powerMeterConf <- configuration) {
      val modules = (for (module <- powerMeterConf('modules).toString.split(",")) yield {
        module match {
          case "procfs-cpu-simple" =>
            ProcFSCpuSimpleModule()
          case "sigar-cpu-simple" =>
            SigarCpuSimpleModule()
          case "cpu-dvfs" =>
            CpuDvfsModule()
          case "libpfm" =>
            LibpfmModule(powerMeterConf('prefix).asInstanceOf[Option[String]], libpfmHelper.get)
          case "libpfm-process" =>
            LibpfmProcessModule(powerMeterConf('prefix).asInstanceOf[Option[String]], libpfmHelper.get)
          case "libpfm-core" =>
            LibpfmCoreModule(powerMeterConf('prefix).asInstanceOf[Option[String]], libpfmHelper.get)
          case "libpfm-core-process" =>
            LibpfmCoreProcessModule(powerMeterConf('prefix).asInstanceOf[Option[String]], libpfmHelper.get)
          case "powerspy" =>
            PowerSpyModule(powerMeterConf('prefix).asInstanceOf[Option[String]])
          case "g5k-omegawatt" =>
            G5kOmegaWattModule(powerMeterConf('prefix).asInstanceOf[Option[String]])
          case "rapl" =>
            RAPLModule()
          case "disk-simple" =>
            DiskSimpleModule(powerMeterConf('prefix).asInstanceOf[Option[String]])
        }
      }).toSeq

      val powerMeter = PowerMeter.loadModule(modules: _*)
      powerMeters :+= powerMeter

      for (monitorConf <- powerMeterConf('monitors).asInstanceOf[List[Map[Symbol, Any]]]) {
        val frequency = monitorConf.getOrElse('frequency, "1000").toString.toInt.milliseconds
        val targets = {
          val uniqueTargets = monitorConf.getOrElse('targets, Set(Process(ManagementFactory.getRuntimeMXBean.getName.split("@")(0).toInt))).asInstanceOf[Set[Target]].toSeq
          if (uniqueTargets.contains(All)) Seq(All) else uniqueTargets
        }
        val agg: Seq[Power] => Power = aggStrToAggFunction(monitorConf.getOrElse('agg, "max").toString.toLowerCase)
        val displays = monitorConf.getOrElse('displays, Set(new ConsoleDisplay)).asInstanceOf[Set[PowerDisplay]]

        val monitor = powerMeter.monitor(targets: _*)(agg).every(frequency)
        monitors :+= monitor

        for (display <- displays) {
          monitor.to(display)
        }
      }
    }

    Thread.sleep(duration.toInt.seconds.toMillis)

    libpfmHelper match {
      case Some(helper) => helper.deinit()
      case _ =>
    }
  }

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}

