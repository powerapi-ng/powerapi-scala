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

import akka.actor.{Actor, PoisonPill}
import akka.event.LoggingReceive
import org.powerapi.core.ActorComponent
import org.powerapi.core.MonitorChannel.MonitorTick
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll}
import scala.collection.BitSet

/**
 * Main contract for a Libpfm picker.
 * A picker is responsible to collect and to scale the PC's value upon request, and to send the scaled value to the enquirer.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
trait LibpfmPicker extends ActorComponent {
  def helper: LibpfmHelper
  def fd: Option[Int]

  def receive: PartialFunction[Any, Unit] = running(true, Array(0,0,0))

  def running(first: Boolean, old: Array[Long]): Actor.Receive = LoggingReceive {
    case monitorTick: MonitorTick => collect(first, old)
    case msg: MonitorStop => stop()
    case _: MonitorStopAll => stop()
  } orElse default

  def collect(first: Boolean, old: Array[Long]): Unit = {
    fd match {
      case Some(fdValue) => {
        val now = helper.readPC(fdValue)

        val scaledValue: Long = {
          if(first) {
            if(now(1) > 0) {
              now(0) * (now(2) / now(1))
            }
            else 0l
          }

          else if(now(1) != old(1) && now(2) != old(2)) {
            helper.scale(now, old) match {
              case Some(value) => value
              case _ => 0l
            }
          }

          else 0l
        }

        log.debug(s"Value read from fd: $scaledValue")

        sender ! scaledValue
        context.become(running(false, now))
      }
      case _ => {}
    }
  }

  def stop(): Unit = {
    self ! PoisonPill
  }
}

/**
 * A DefaultLibpfmPicker has to open the PC (represented as a fd).
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class DefaultLibpfmPicker(val helper: LibpfmHelper, event: String, pid: Int, cpu: Int, configuration: BitSet) extends LibpfmPicker {
  private var _fd: Option[Int] = None

  def fd: Option[Int] = {
    if(_fd == None) {
      helper.configurePC(pid, cpu, configuration, event) match {
        case Some(value: Int) => {
          helper.resetPC(value)
          helper.enablePC(value)
          _fd = Some(value)
        }
        case None => {
          log.warning("Libpfm is not able to open the counter (pid: {}, cpu: {})", pid, cpu)
          _fd = None
        }
      }
    }

    _fd
  }

  override def postStop(): Unit = {
    fd match {
      case Some(fdValue) => {
        helper.disablePC(fdValue)
        helper.closePC(fdValue)
      }
      case _ => {}
    }

    super.postStop()
  }
}

/**
 * A FDLibpfmPicker uses a given file descriptor (attached to a PC).
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class FDLibpfmPicker(val helper: LibpfmHelper, val fd: Option[Int]) extends LibpfmPicker
