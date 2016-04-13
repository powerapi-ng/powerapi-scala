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

import java.util.UUID

import scala.collection.BitSet
import scala.concurrent.Future

import akka.actor.{Actor, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout

import org.powerapi.core.MessageBus
import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick, unsubscribeMonitorTick}
import org.powerapi.core.target.{All, Target}
import org.powerapi.module.Sensor
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, LibpfmPickerStop, PCWrapper, formatLibpfmCoreSensorChildName, publishPCReport}

/**
  * Libpfm sensor component that collects metrics with libpfm at a core level.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class LibpfmCoreSensor(eventBus: MessageBus, muid: UUID, target: Target, libpfmHelper: LibpfmHelper, timeout: Timeout,
                       topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String]) extends Sensor(eventBus, muid, target) {

  val combinations = {
    for {
      core: Int <- topology.keys
      index: Int <- topology(core)
      event: String <- events
    } yield (core, index, event)
  }.toParArray

  def init(): Unit = subscribeMonitorTick(muid, target)(eventBus)(self)

  def terminate(): Unit = {
    context.actorSelection("*") ! LibpfmPickerStop
    unsubscribeMonitorTick(muid, target)(eventBus)(self)
  }

  def handler: Actor.Receive = {
    if (target == All) {
      combinations.foreach {
        case (_, index, event) =>
          val name = formatLibpfmCoreSensorChildName(index, event, muid)
          context.actorOf(Props(classOf[LibpfmPicker], libpfmHelper, event, index, None, configuration), name)
      }

      sense
    }

    else {
      unsubscribeMonitorTick(muid, target)(eventBus)(self)
      self ! PoisonPill
      sensorDefault
    }
  }

  def sense: Actor.Receive = {
    case msg: MonitorTick =>
      val allWrappers = combinations.map {
        case (core, index, event) =>
          val name = formatLibpfmCoreSensorChildName(index, event, muid)
          val actor = context.child(name).get
          PCWrapper(core, event, List(actor.?(msg.tick)(timeout).asInstanceOf[Future[HWCounter]]))
      }

      publishPCReport(muid, target, allWrappers.groupBy(wrapper => (wrapper.core, wrapper.event)).map {
        case ((core, event), wrappers) => PCWrapper(core, event, wrappers.flatMap(_.values).toList)
      }.toList, msg.tick)(eventBus)
  }
}
