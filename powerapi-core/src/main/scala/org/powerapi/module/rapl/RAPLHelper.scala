/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2014 Inria, University of Lille 1.
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
package org.powerapi.module.rapl

import java.io.{IOException, FileNotFoundException, FileInputStream}
import java.nio.channels.FileChannel
import java.nio.{ByteOrder, ByteBuffer}
import org.apache.logging.log4j.LogManager
import org.powerapi.core.{ConfigValue, Configuration}
import scala.sys.process.stringSeqToProcess

/**
 * Collecting energy information contained into RAPL registers (MSR)
 * and providing the CPU energy.
 *
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
class RAPLHelper extends Configuration {
  private val log = LogManager.getLogger

  /**
   * CPU info file, giving information about specifications of the processor.
   */
  lazy val cpuInfoURL = load { _.getString("rapl.cpuInfoURL") } match {
    case ConfigValue(url) => url
    case _ => "/proc/cpuinfo"
  }

  /**
   * MSR registers URL, giving information about estimation (from RAPL model) of CPU energy consumption.
   */
  lazy val msrURL = load { _.getString("rapl.msrURL") } match {
    case ConfigValue(url) => url
    case _ => "/dev/cpu/0/msr"
  }

  /* Platform specific RAPL Domains */
  private val MSR_RAPL_POWER_UNIT   = 0x606
  private val MSR_PKG_ENERGY_STATUS	= 0x611

  private val data = ByteBuffer.allocate(java.lang.Long.SIZE / java.lang.Byte.SIZE)
  data.order(ByteOrder.nativeOrder)

  /* Architecture ID */
  private val archs = collection.mutable.Map(
    42 -> "Sandybridge",
    45 -> "Sandybridge-EP",
    58 -> "Ivybridge",
    62 -> "Ivybridge-EP",
    60 -> "Haswell"
  )

  /* Related to MSR reading */
  lazy val msrFile: Option[FileChannel] = {
    if(detectCpu) {
      Seq("modprobe", "msr").!
      Some(new FileInputStream(msrURL).getChannel)
    }
    else None
  }

  lazy val powerUnits = Math.pow(0.5, readMsr(MSR_RAPL_POWER_UNIT) & 0xf)
  lazy val energyUnits = Math.pow(0.5,(readMsr(MSR_RAPL_POWER_UNIT) >> 8) & 0x1f)
  lazy val timeUnits = Math.pow(0.5, (readMsr(MSR_RAPL_POWER_UNIT) >> 16) & 0xf)

  def getRAPLEnergy: Double = readMsr(MSR_PKG_ENERGY_STATUS) * energyUnits

  def close(): Unit = {
    msrFile match {
      case Some(file) => file.close()
      case _ => {}
    }
  }

  private def readMsr(which: Int): Long = {
    msrFile match {
      case Some(file) => {
        try {
          data.clear()
          file.read(data, which)
          data.rewind()
          data.getLong
        }
        catch {
          case fnfe: FileNotFoundException => { //if this exception occur, type 'modprobe msr'
            log.error("rdmsr: file not found exception: {}", fnfe.getMessage)
            0l
          }
          case ioe: IOException => {
            log.error("rdmsr: i/o exception: {}", ioe.getMessage)
            0l
          }
          case npe: NullPointerException => {
            log.error("rdmsr: null pointer exception: {}", npe.getMessage)
            0l
          }
        }
      }
      case _ => 0l
    }
  }

  private def detectCpu: Boolean = {
    val source = io.Source.fromFile(cpuInfoURL).getLines
    source.find(l => l.startsWith("vendor_id") && l.endsWith("GenuineIntel")) match {
      case Some(_) => source.find(l => l.startsWith("cpu family") && l.endsWith("6")) match {
        case Some(_) => source.find(_.startsWith("model")) match {
          case Some(model) => archs.getOrElse(model.split("\\s").last.toInt, "") match {
            case ""         => log.error("cpuinfo: Unsupported model {}", model); false
            case modelCheck => log.info("Found {} CPU", modelCheck); true
          }
          case None => log.error("cpuinfo: CPU model missing"); false
        }
        case None => log.error("cpuinfo: Wrong CPU family"); false
      }
      case None => log.error("cpuinfo: This CPU is not an Intel chip"); false
    }
  }
}
