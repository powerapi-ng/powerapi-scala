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

import akka.actor.{Actor, PoisonPill}
import akka.event.LoggingReceive
import java.util.UUID
import org.powerapi.core.ActorComponent
import org.powerapi.core.target.Target
import org.powerapi.core.MonitorChannel.MonitorTick
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll}
import scala.collection.BitSet

/**
 * Base trait for each LibpfmCoreSensorChild.
 * A LibpfmCoreSensorChild is reponsible to handle one performance counter, to collect its value and then to process the result.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmCoreSensorChild(event: String, core: Int, tid: Option[Int], configuration: BitSet) extends ActorComponent {
  private var _fd: Option[Int] = None

  def fd: Option[Int] = {
    if(_fd == None) {
      val identifier = tid match {
        case Some(value) => TCID(value, core)
        case None => CID(core)
      }

      LibpfmHelper.configurePC(identifier, configuration, event) match {
        case Some(value: Int) => {
          LibpfmHelper.resetPC(value)
          LibpfmHelper.enablePC(value)
          _fd = Some(value)
        }
        case None => {
          log.warning("Libpfm is not able to open the counter for the identifier {}", identifier)
          _fd = None
        }
      }
    }

    _fd
  }

  override def postStop(): Unit = {
    fd match {
      case Some(fdValue) => {
        LibpfmHelper.disablePC(fdValue)
        LibpfmHelper.closePC(fdValue)
      }
      case _ => {}
    }
  }

  def receive: PartialFunction[Any, Unit] = running(true, Array(0,0,0))

  def running(first: Boolean, old: Array[Long]): Actor.Receive = LoggingReceive {
    case monitorTick: MonitorTick => collect(monitorTick.muid, monitorTick.target, first, old)
    case msg: MonitorStop => stop()
    case _: MonitorStopAll => stop()
  } orElse default

  def collect(muid: UUID, target: Target, first: Boolean, old: Array[Long]): Unit = {
    fd match {
      case Some(fdValue) => {
        val now = LibpfmHelper.readPC(fdValue)

        val scaledValue: Long = {
          if(first) {
            0l
          }

          else if(now(1) != old(1) && now(2) != old(2)) {
            LibpfmHelper.scale(now, old) match {
              case Some(value) => value
              case _ => 0l
            }
          }

          else 0l
        }

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
