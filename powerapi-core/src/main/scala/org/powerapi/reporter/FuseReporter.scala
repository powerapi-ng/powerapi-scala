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
package org.powerapi.reporter

import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

import scalax.file.Path
import scala.concurrent.Await
import scala.concurrent.duration.{ DurationLong, FiniteDuration }

import akka.actor.{ Actor, ActorLogging, ActorRef, PoisonPill, Props }
import akka.event.LoggingReceive
import akka.pattern.ask

import net.fusejna.DirectoryFiller
import net.fusejna.ErrorCodes
import net.fusejna.FuseException
import net.fusejna.StructFuseFileInfo.FileInfoWrapper
import net.fusejna.StructStat.StatWrapper
import net.fusejna.types.TypeMode.{ ModeWrapper, NodeType }
import net.fusejna.util.FuseFilesystemAdapterFull

import org.powerapi.{ PowerMeter, PowerMonitoring }
import org.powerapi.core.{APIComponent, ConfigValue, Configuration}
import org.powerapi.core.power._
import org.powerapi.core.target._
import org.powerapi.module.PowerChannel.AggregatePowerReport


/**
 * Main Configuration
 */
class FuseReporterConfiguration extends Configuration(None) {
  lazy val fuseFileName: String = load { _.getString("powerapi.fuse.filename") } match {
    case ConfigValue(value) => value
    case _ => "./test"
  }
}

case class StartProcessMonitoring(frequency: FiniteDuration, targets: Set[String])
case class StopProcessMonitoring(targets: Set[String])

/**
 * Dedicated type of message to get each monitored processes with
 * it corresponding energy consumption.
 */
object GetEnergyInfo

object StopFuse

/**
 * Display power information into a virtual file system using FUSE tool.
 *
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class FuseReporter(pm: PowerMeter) extends FuseReporterConfiguration with APIComponent {
  private lazy val powerAPIFuse = new PowerAPIFuse(self, fuseFileName)
  private var powerAPIFuseThread: Option[Thread] = None

  // monitored processes with it corresponding energy consumption.
  // The value is compute from aggregate power reports.
  // [process -> (timestamp, energy, power, muid)]
  lazy val energyInfo = collection.mutable.HashMap[Set[Target], (Long, Double, Double, Option[UUID])]()
  

  override def preStart() {
    powerAPIFuseThread = Some(new Thread(powerAPIFuse))
    powerAPIFuseThread.get.start
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: AggregatePowerReport => report(msg)
    case msg: StartProcessMonitoring => start(msg)
    case msg: StopProcessMonitoring => stop(msg)
    case StopFuse => stopAll
    case GetEnergyInfo => getEnergyInfo
  } orElse default

  def report(aggPowerReport: AggregatePowerReport): Unit = {
    if (energyInfo contains aggPowerReport.targets) {
      energyInfo(aggPowerReport.targets) = (aggPowerReport.tick.timestamp,
                                            aggPowerReport.power.toWatts,
                                            energyInfo(aggPowerReport.targets)._3 + aggPowerReport.power.toWatts,
                                            Some(aggPowerReport.muid))
    }
  }
  
  def start(msg: StartProcessMonitoring): Unit = {
    val targets: Set[Target] = for (target <- msg.targets) yield {
      if (target forall Character.isDigit) Process(target.toInt) else Application(target)
    }
    if (!energyInfo.contains(targets))
      pm.monitor(msg.frequency)(targets.toSeq:_*) to self
  }
  
  def stop(msg: StopProcessMonitoring): Unit = {
    val targets: Set[Target] = for (target <- msg.targets) yield {
      if (target forall Character.isDigit) Process(target.toInt) else Application(target)
    }
    if (energyInfo contains targets)
      energyInfo(targets)._4 match {
        case Some(muid) => pm.stopMonitor(muid)
        case None => log.warning("target(s) {} doesn't exists", targets)
      }
  }
  
  def stopAll: Unit = {
    powerAPIFuseThread match {
      case Some(thread) => {
        powerAPIFuse.unmount
        thread.interrupt
      }
      case _ => log.error("The FUSE thread is not created")
    }
    self ! PoisonPill
  }
  
  def getEnergyInfo: Unit = {
    val currentProcesses = pm.getMonitoredProcesses.toSet
    val oldProcesses = energyInfo.keySet -- currentProcesses
    energyInfo --= oldProcesses
    val newProcesses = currentProcesses -- energyInfo.keySet
    newProcesses.foreach(targets =>
      energyInfo += (targets -> energyInfo.getOrElse(targets, (0, 0.0, 0.0, None)))
    )
    sender ! energyInfo.clone
  }
}

class PowerAPIFuse(reporter: ActorRef, fuseFileName: String) extends FuseFilesystemAdapterFull with Runnable {
  // ------------------
  // --- Thread part --------------------------------------------------

  def run = this.log(false).mount(mountPoint)

  // ---------------------
  // --- FUSE-jna part --------------------------------------------------

  lazy val mountPoint = {
    Path.fromString(fuseFileName).createDirectory(createParents=false, failIfExists=false)
    fuseFileName
  }

  val pidsFileName = "pids"
  lazy val conf = collection.mutable.HashMap[String, String](
    ("frequency" -> "1")
  )
  lazy val Dir = """/|/energy""".r
  lazy val EnergyPidFileFormat = """/energy/([\w+(,)?]+)/(\w+)""".r
  lazy val EnergyPidDirFormat  = """/energy/([\w+(,)?]+)""".r
  
  
  override def getattr(path: String, stat: StatWrapper) =
    path match {
      case Dir() => {
        stat.setMode(NodeType.DIRECTORY)
        0
      }
      case EnergyPidDirFormat(pid) if energyInfo contains pid => {
        stat.setMode(NodeType.DIRECTORY)
        0
      }
      case EnergyPidFileFormat(pid, file) if ((energyInfo contains pid) && (conf contains file)) => {
        stat.setMode(NodeType.FILE).size(conf(file).length+1)
        0
      }
      case EnergyPidFileFormat(pid, file) if ((energyInfo contains pid) && (file == "energy")) => {
        while (energyInfo.apply(pid)._1 <= 0) Thread.sleep(1000)
        val v = energyInfo.apply(pid)
        stat.setMode(NodeType.FILE).size((pid + " " + v._3 + "\n").length+1)
        0
      }
      case EnergyPidFileFormat(pid, file) if ((energyInfo contains pid) && (file == "power")) => {
        while (energyInfo.apply(pid)._1 <= 0) Thread.sleep(1000)
        val v = energyInfo.apply(pid)
        stat.setMode(NodeType.FILE).size((pid + " " + v._1 + " " + v._2).length+1)
        0
      }
      case _ => -ErrorCodes.ENOENT()
    }

  override def read(path: String, buffer: ByteBuffer, size: Long, offset: Long, info: FileInfoWrapper) =
  {
    val content = path match {
      case EnergyPidFileFormat(pid, file) if ((energyInfo contains pid) && (conf contains file)) => conf(file) + "\n"
      case EnergyPidFileFormat(pid, file) if ((energyInfo contains pid) && (file == "energy")) => {
        while (energyInfo.apply(pid)._1 <= 0) Thread.sleep(1000)
        val v = energyInfo.apply(pid)
        pid + " " + v._3 + "\n"
      }
      case EnergyPidFileFormat(pid, file) if ((energyInfo contains pid) && (file == "power")) => {
        while (energyInfo.apply(pid)._1 <= 0) Thread.sleep(1000)
        val v = energyInfo.apply(pid)
        pid + " " + v._1 + " " + v._2 + "\n"
      }
      case _ => ""
    }
    
    val s = content.substring(offset.asInstanceOf[Int],
                Math.max(offset,
                         Math.min(content.length() - offset, offset + size)
                ).asInstanceOf[Int])
    buffer.put(s.getBytes())
    s.getBytes().length
  }
  
  override def readdir(path: String, filler: DirectoryFiller) =
    path match {
      case File.separator => {
        filler.add("energy")
        0
      }
      case "/energy" => {
        energyInfo.keySet.foreach(pid => filler.add(pid))
        0
      }
      case EnergyPidDirFormat(pid) if energyInfo contains pid => {
        conf.keySet.foreach(confFile => filler.add(confFile))
        filler.add("power")
        filler.add("energy")
        0
      }
      case _ => -ErrorCodes.ENOENT()
    }
    
  override def mkdir(path: String, mode: ModeWrapper) =
    path match {
      case EnergyPidDirFormat(pid) if !(energyInfo contains pid) => {
        reporter ! StartProcessMonitoring(conf("frequency").toLong.seconds, pid.split(",").toSet)
        0
      }
      case _ => -ErrorCodes.ENOENT()
    }
    
  override def rmdir(path: String) =
    path match {
      case EnergyPidDirFormat(pid) if energyInfo contains pid => {
        reporter ! StopProcessMonitoring(pid.split(",").toSet)
        0
      }
      case _ => -ErrorCodes.ENOENT()
    }
    
  private def energyInfo = Await.result(
    reporter.ask(GetEnergyInfo)(5.seconds), 5.seconds
  ).asInstanceOf[collection.mutable.HashMap[Set[Target], (Long, Double, Double, Option[UUID])]].map(
    entry => (entry._1.mkString(",") -> entry._2)
  )
}

