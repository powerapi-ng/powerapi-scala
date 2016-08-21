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
package org.powerapi.module.libpfm

import scala.collection.BitSet

import akka.actor.Actor

import org.powerapi.core.{ActorComponent, Tick}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, LibpfmPickerStop}

/**
  * Base trait for each LibpfmPicker.
  * A LibpfmPicker is responsible to handle one performance counter, to collect and to process its values.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class LibpfmPicker(helper: LibpfmHelper, event: String, core: Int, tid: Option[Int], containerName: Option[String], configuration: BitSet) extends ActorComponent {

  def receive: Actor.Receive = {
    val fd: Option[Int] = {
      val identifier = {
        if (containerName.isDefined) {
          CGID(containerName.get, core)
        }
        else if (tid.isDefined) {
          TCID(tid.get, core)
        }
        else CID(core)
      }

      helper.configurePC(identifier, configuration, event) match {
        case Some(_fd) =>
          helper.resetPC(_fd)
          helper.enablePC(_fd)
          Some(_fd)
        case None =>
          log.warning("Libpfm is not able to open the counter for the identifier {}", identifier)
          None
      }
    }

    val initValues = fd match {
      case Some(_fd) => helper.readPC(_fd)
      case None => Array[Long](0, 0, 0)
    }

    running(fd, initValues)
  }

  def running(fd: Option[Int], old: Array[Long]): Actor.Receive = {
    case _: Tick =>
      fd match {
        case Some(_fd) =>
          val now = helper.readPC(_fd)
          val value = helper.scale(now, old) match {
            case Some(_value) => _value
            case None => 0l
          }

          sender ! HWCounter(value)
          context.become(running(fd, now))

        case None =>

      }
    case LibpfmPickerStop =>
      fd match {
        case Some(_fd) =>
          helper.disablePC(_fd)
          helper.closePC(_fd)
        case None =>

      }
  }
}
