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
package org.powerapi.module.libpfm

/**
 * Internal wrappers
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
trait Identifier
case class TID(identifier: Int) extends Identifier
case class CID(core: Int) extends Identifier
case class TCID(identifier: Int, core: Int) extends Identifier

/**
 * This object allows us to interact with the Libpfm library (C Library).
 * We use jnaerator and bridj to create the binding.
 *
 * @see https://github.com/ochafik/nativelibs4java
 * @see http://www.man7.org/linux/man-pages/man2/perf_event_open.2.html.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object LibpfmHelper {
  import scala.collection.BitSet
  import org.apache.logging.log4j.LogManager
  import org.bridj.Pointer.{allocateCLongs, pointerTo, pointerToCString}
  import perfmon2.libpfm.{LibpfmLibrary, perf_event_attr, pfm_perf_encode_arg_t}
  import perfmon2.libpfm.LibpfmLibrary.pfm_os_t

  private val format = LibpfmLibrary.perf_event_read_format.PERF_FORMAT_TOTAL_TIME_ENABLED.value().toInt | LibpfmLibrary.perf_event_read_format.PERF_FORMAT_TOTAL_TIME_RUNNING.value.toInt
  private var initialized = false

  private val log = LogManager.getLogger

  /**
   * Implicit conversion BitSet to Long
   */
  implicit def BitSet2Long(value: BitSet): Long = {
    // We limit the size of the bitset (see the documentation on perf_event.h, only 23 bits for the config.)
    // The other 41 bits are reserved.
    value.range(0, 23).foldLeft(0l)((acc, index) => acc + (1L << index))
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
    LibpfmLibrary.pfm_terminate()
    initialized = false
  }

  /**
   * Open a file descriptor with the given configuration options.
   *
   * @param identifier: identifier used to open the counter
   * @param configuration: bits configuration
   * @param name: name of the performance counter to open
   */
  def configurePC(identifier: Identifier, configuration: BitSet, name: String): Option[Int] = {
    val cName = pointerToCString(name)
    val argEncoded = new pfm_perf_encode_arg_t
    val argEncodedPointer = pointerTo(argEncoded)
    val eventAttr = new perf_event_attr
    val eventAttrPointer = pointerTo(eventAttr)

    argEncoded.attr(eventAttrPointer)

    // Get the specific event encoding for the OS.
    // PFM_PLM3: measure at user level (including PFM_PLM2, PFM_PLM1).
    // PFM_PLM0: measure at kernel level.
    // PFM_PLMH: measure at hypervisor level.
    // PFM_OS_PERF_EVENT_EXT is used to extend the default perf_event library with libpfm.
    val ret = LibpfmLibrary.pfm_get_os_event_encoding(cName, LibpfmLibrary.PFM_PLM0|LibpfmLibrary.PFM_PLM3|LibpfmLibrary.PFM_PLMH, pfm_os_t.PFM_OS_PERF_EVENT, argEncodedPointer)

    if(ret == LibpfmLibrary.PFM_SUCCESS) {
      // Set the bits in the structure.
      eventAttr.read_format(format)
      eventAttr.bits_config(configuration: Long)

      // Open the file descriptor.
      val fd = identifier match {
        case TID(tid) => CUtils.perf_event_open(eventAttrPointer, tid, -1, -1, 0)
        case CID(cid) => CUtils.perf_event_open(eventAttrPointer, -1, cid, -1, 0)
        case TCID(tid, cid) => CUtils.perf_event_open(eventAttrPointer, tid, cid, -1, 0)
        case _ => {
          log.error("The type of the first parameter is unknown.")
          -1
        }
      }

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
    CUtils.ioctl(fd, LibpfmLibrary.PERF_EVENT_IOC_RESET) == 0
  }

  /**
   * Enable the performance counter represented by a file descriptor.
   */
  def enablePC(fd: Int): Boolean = {
    CUtils.ioctl(fd, LibpfmLibrary.PERF_EVENT_IOC_ENABLE) == 0
  }

  /**
   * Disable the performance counter represented by a file descriptor.
   */
  def disablePC(fd: Int): Boolean = {
    CUtils.ioctl(fd, LibpfmLibrary.PERF_EVENT_IOC_DISABLE) == 0
  }

  /**
   * Close the performance counter represented by a file descriptor.
   */
  def closePC(fd: Int): Boolean = {
    CUtils.close(fd) == 0
  }

  /**
   * Read the values from the performance counter represented by a file descriptor.
   */
  def readPC(fd: Int): Array[Long] = {
    val counts = allocateCLongs(3)
    // 8 bytes * 3 longs
    if(CUtils.read(fd, counts, 8 * 3) > -1) {
      counts.getCLongs
    }
    else Array(0L, 0L, 0L)
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
}
