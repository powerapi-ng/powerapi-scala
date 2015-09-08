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
package org.powerapi.module.libpfm

import java.nio.{ByteBuffer, ByteOrder}
import com.sun.jna.Native
import org.apache.logging.log4j.LogManager
import org.bridj.Pointer.{getPointer, pointerToCString}
import org.powerapi.core.{Configuration, ConfigValue}
import perfmon2.libpfm.{pfm_pmu_info_t, perf_event_attr, pfm_perf_encode_arg_t, LibpfmLibrary, pfm_event_info_t, pfm_event_attr_info_t}
import perfmon2.libpfm.LibpfmLibrary.{pfm_attr_t, pfm_pmu_t, pfm_os_t}
import scala.collection.BitSet

case class Event(pmu: String, name: String, code: String) extends Ordered[Event] {
  override def equals(that: Any): Boolean = {
    that.isInstanceOf[Event] && java.lang.Long.parseLong(code, 16) == java.lang.Long.parseLong(that.asInstanceOf[Event].code, 16) && (pmu.equals("*") || pmu.equals(that.asInstanceOf[Event].name))
  }

  override def compare(that: Event): Int = {
    pmu.compare(that.pmu) + name.compare(that.name)
  }
}

// We consider only the generic counters but we need to distinguish the Intel PMU (with fixed counters) and the others.
case class PMU(name: String, nbGenericCounters: Int, events: List[Event])

object IntelPMU {
  val fixedEvents = List[Event](Event("*", "FIXED_CTR0", "3C"), Event("*", "FIXED_CTR1", "300"), Event("*", "FIXED_CTR1_BIS", "13C"), Event("*", "FIXED_CTR2", "C0"), Event("*", "UNC_FIXED_CTR0", "FF"))
}

/**
 * This object allows us to interact with the Libpfm library (C Library).
 * We use jnaerator and bridj to create the binding.
 *
 * @see https://github.com/ochafik/nativelibs4java
 * @see http://www.man7.org/linux/man-pages/man2/perf_event_open.2.html.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmHelper extends Configuration {
  private val format = LibpfmLibrary.perf_event_read_format.PERF_FORMAT_TOTAL_TIME_ENABLED.value().toInt | LibpfmLibrary.perf_event_read_format.PERF_FORMAT_TOTAL_TIME_RUNNING.value.toInt
  private var initialized = false
  private val log = LogManager.getLogger
  private val cUtilsJNA = Native.loadLibrary("c", classOf[CUtilsJNA]).asInstanceOf[CUtilsJNA]

  lazy val nrPerfEventOpen = load { _.getInt("powerapi.libpfm.NR-perf-event-open") } match {
    case ConfigValue(value) => value
    case _ => 298 // Linux Intel/AMD 64 bits.
  }

  /**
   * Init. libpfm
   */
  def init(): Boolean = {
    if(!initialized) {
      val ret = LibpfmLibrary.pfm_initialize()

      if(ret == LibpfmLibrary.PFM_SUCCESS) {
        initialized = true
        true
      }
      else {
        initialized = false
        throw new RuntimeException("Libpfm cannot be initialized.")
      }
    }
    else {
      log.debug("Libpfm is already initialized.")
      true
    }
  }

  /**
   * Deinit. libpfm
   */
  def deinit(): Unit = {
    if(initialized) {
      LibpfmLibrary.pfm_terminate()
      initialized = false
    }
  }

  /**
   * Open a file descriptor with the given configuration options.
   *
   * @param pid: process to monitor (could be also a Thread IDentifier)
   * @param cpu: attached CPU to monitor
   * @param configuration: bits configuration
   * @param name: event name
   * @param group: event group (only if needed) (default: -1)
   * @param flags: specify more options (only if needed) (default: 0)
   */
  def configurePC(pid: Int, cpu: Int, configuration: BitSet, name: String, group: Int = -1, flags: Long = 0): Option[Int] = {
    val cName = pointerToCString(name)
    val argEncoded = new pfm_perf_encode_arg_t
    val argEncodedPointer = getPointer(argEncoded)
    val eventAttr = new perf_event_attr
    val eventAttrPointer = getPointer(eventAttr)

    argEncoded.attr(eventAttrPointer)

    // Get the specific event encoding for the OS.
    // PFM_PLM3: measure at user level (including PFM_PLM2, PFM_PLM1).
    // PFM_PLM0: measure at kernel level.
    // PFM_PLMH: measure at hypervisor level.
    // PFM_OS_PERF_EVENT_EXT is used to extend the default perf_event library with libpfm.
    val ret = LibpfmLibrary.pfm_get_os_event_encoding(cName, LibpfmLibrary.PFM_PLM0|LibpfmLibrary.PFM_PLM3|LibpfmLibrary.PFM_PLMH, pfm_os_t.PFM_OS_PERF_EVENT_EXT, argEncodedPointer)

    if(ret == LibpfmLibrary.PFM_SUCCESS) {
      // Set the bits in the structure.
      eventAttr.read_format(format)
      eventAttr.bits_config(configuration: Long)

      val fd = CUtilsBridJ.perf_event_open(nrPerfEventOpen, eventAttrPointer, pid, cpu, group, flags)

      if(fd > 0) {
        Some(fd)
      }

      else {
        log.warn("Libpfm is not able to open a counter for the event {}.", name)
        None
      }
    }

    else {
      log.warn("Libpfm cannot initialize the structure for this event.")
      None
    }
  }

  /**
   * Reset the performance counter represented by a file descriptor.
   */
  def resetPC(fd: Int): Boolean = {
    cUtilsJNA.ioctl(fd, LibpfmLibrary.PERF_EVENT_IOC_RESET) == 0
  }

  /**
   * Enable the performance counter represented by a file descriptor.
   */
  def enablePC(fd: Int): Boolean = {
    cUtilsJNA.ioctl(fd, LibpfmLibrary.PERF_EVENT_IOC_ENABLE) == 0
  }

  /**
   * Disable the performance counter represented by a file descriptor.
   */
  def disablePC(fd: Int): Boolean = {
    cUtilsJNA.ioctl(fd, LibpfmLibrary.PERF_EVENT_IOC_DISABLE) == 0
  }

  /**
   * Close the performance counter represented by a file descriptor.
   */
  def closePC(fd: Int): Boolean = {
    cUtilsJNA.close(fd) == 0
  }

  /**
   * Read the values from the performance counter represented by a file descriptor.
   */
  def readPC(fd: Int): Array[Long] = {
    // 8 bytes * 3 longs
    val bytes = new Array[Byte](24)
    val buffer = ByteBuffer.allocate(8)
    buffer.order(ByteOrder.nativeOrder())

    if(cUtilsJNA.read(fd, bytes, 24) > -1) {
      (for(i <- 0 until (24, 8)) yield {
        buffer.clear()
        buffer.put(bytes.slice(i, i + 8))
        buffer.flip()
        buffer.getLong
      }).toArray
    }

    else Array(0l, 0l, 0l)
  }

  /**
   * Allows to scale the values read from a performance counter by applying a ratio between the enabled/running times.
   */
  def scale(now: Array[Long], old: Array[Long]): Option[Long] = {
    /* [0] = raw count
     * [1] = TIME_ENABLED
     * [2] = TIME_RUNNING
     */
    if(now(2) == 0 && now(1) == 0 && now(0) != 0) {
      log.warn("time_running = 0 = time_enabled, raw count not zero.")
      None
    }

    else if(now(2) > now(1)) {
      log.warn("time_running > time_enabled.")
      None
    }

    else if(now(2) - old(2) > 0) {
      Some(((now(0) - old(0)) * ((now(1) - old(1)) / (now(2) - old(2))).toDouble).round)
    }

    else None
  }

  /**
   * PMUs with theirs associated events detected on the processor.
   * All the generic PMUs are removed because they used the specifics ones for the encoding.
   */
  def availablePMUS(): List[PMU] = {
    // Generic and RAPL PMUs are removed.
    val generics = Array(pfm_pmu_t.PFM_PMU_INTEL_X86_ARCH, pfm_pmu_t.PFM_PMU_PERF_EVENT, pfm_pmu_t.PFM_PMU_PERF_EVENT_RAW, pfm_pmu_t.PFM_PMU_INTEL_RAPL)
    val allSupportedPMUS = pfm_pmu_t.values().to[scala.collection.mutable.ArrayBuffer] -- generics
    val pmus = scala.collection.mutable.ArrayBuffer[PMU]()

    for(pmu <- allSupportedPMUS) {
      val pinfo = new pfm_pmu_info_t
      val pinfoPointer = getPointer(pinfo)
      val ret = LibpfmLibrary.pfm_get_pmu_info(pmu, pinfoPointer)

      // The bit is_present is checked to know whether a PMU is available. A shift is done because of a jnaerator/bridj limitation with bit fields struct.
      if(ret == LibpfmLibrary.PFM_SUCCESS && ((pinfo.bits_def >> 32) & 1) == 1) {
        val events = detectsEvents(pinfo)
        val nbGenericCounters = detectsNbGenericCounters(pmu, pinfo, events)
        log.info("PMU {} detected, number of generic counters: {}", pinfo.name.getCString, s"$nbGenericCounters")
        pmus += PMU(pinfo.name.getCString, nbGenericCounters, events)
      }
    }

    pmus.toList
  }

  /**
   * Detects the events associated to a given PMU.
   */
  private def detectsEvents(pmu: pfm_pmu_info_t): List[Event] = {
    val einfo = new pfm_event_info_t
    val einfoPointer = getPointer(einfo)
    var index = pmu.first_event
    val events = scala.collection.mutable.Set[Event]()

    while(index != -1) {
      if (LibpfmLibrary.pfm_get_event_info(index, pfm_os_t.PFM_OS_PERF_EVENT, einfoPointer) == LibpfmLibrary.PFM_SUCCESS) {
        // If there is no equivalent event, we can keep the event.
        if (einfo.equiv == null) {
          var noMask = true

          // Events with umask.
          for (i <- 0 until einfo.nattrs) yield {
            val ainfo = new pfm_event_attr_info_t
            val ainfoPointer = getPointer(ainfo)

            if (LibpfmLibrary.pfm_get_event_attr_info(einfo.idx, i, pfm_os_t.PFM_OS_PERF_EVENT, ainfoPointer) == LibpfmLibrary.PFM_SUCCESS && ainfo.`type`.value == pfm_attr_t.PFM_ATTR_UMASK.value) {
              events += Event(s"${pmu.name.getCString}", s"${einfo.name.getCString}:${ainfo.name.getCString}", s"${ainfo.code.toHexString}${einfo.code.toHexString}")
              noMask = false
            }
          }

          // Only modifiers.
          if (noMask) {
            events += Event(s"${pmu.name.getCString}", s"${einfo.name.getCString}", s"${einfo.code.toHexString}")
          }
        }

        index = LibpfmLibrary.pfm_get_event_next(index)
      }
    }

    events.toList.sorted
  }

  /**
   * Infers the number of available slots for the generic events.
   */
  private def detectsNbGenericCounters(pmu: pfm_pmu_t, pinfo: pfm_pmu_info_t, events: List[Event]): Int = {
    val genericEvents = if(pmu.name.toLowerCase.indexOf("intel") != -1) {
      events.filter(!IntelPMU.fixedEvents.contains(_))
    } else events

    var nbEvents = 0
    val fds = scala.collection.mutable.ArrayBuffer[Option[Int]]()
    var scaling = false

    while(!scaling && nbEvents <= genericEvents.size) {
      val fd = configurePC(-1, 0, BitSet(), s"${pinfo.name.getCString}::${genericEvents(nbEvents).name}")
      fds += fd

      fd match {
        case Some(fdVal) => resetPC(fdVal); enablePC(fdVal)
        case None => {}
      }

      fd match {
        case Some(fdVal) => {
          val now = readPC(fdVal)
          if(now(2) / now(1).toDouble < 0.99) scaling = true
        }
        case None => {}
      }

      nbEvents += 1
    }

    for(fd <- fds) fd match {
      case Some(fdVal) => disablePC(fdVal); closePC(fdVal)
      case None => {}
    }

    nbEvents - 1
  }
}
