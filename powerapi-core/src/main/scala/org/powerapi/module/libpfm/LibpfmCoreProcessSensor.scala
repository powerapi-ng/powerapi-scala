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
import scala.concurrent.Await

import akka.actor.{Actor, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout

import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick, unsubscribeMonitorTick}
import org.powerapi.core.target.{All, Target}
import org.powerapi.core.{MessageBus, OSHelper}
import org.powerapi.module.Sensor
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, LibpfmPickerStop, formatLibpfmCoreProcessSensorChildName, publishPCReport}

/**
  * Libpfm sensor component that collects metrics with libpfm at a core level.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class LibpfmCoreProcessSensor(eventBus: MessageBus, muid: UUID, target: Target, osHelper: OSHelper, libpfmHelper: LibpfmHelper, timeout: Timeout,
                              topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String], inDepth: Boolean) extends Sensor(eventBus, muid, target) {

  def init(): Unit = subscribeMonitorTick(muid, target)(eventBus)(self)

  def terminate(): Unit = {
    context.actorSelection("*") ! LibpfmPickerStop
    unsubscribeMonitorTick(muid, target)(eventBus)(self)
  }

  def currentIdentifiers: Set[Int] = {
    if (inDepth) osHelper.getProcesses(target).flatMap(process => osHelper.getThreads(process).map(_.tid) + process.pid)
    else osHelper.getProcesses(target).map(_.pid)
  }

  def handler: Actor.Receive = {
    if (target != All) {
      val initIdentifiers = currentIdentifiers

      if (initIdentifiers.isEmpty) {
        self ! PoisonPill
        sensorDefault
      }

      else {
        val combinations = {
          for {
            core: Int <- topology.keys
            index: Int <- topology(core)
            event: String <- events
            id: Int <- initIdentifiers
          } yield (core, index, event, id)
        }

        combinations.foreach {
          case (_, index, event, id) =>
            val name = formatLibpfmCoreProcessSensorChildName(index, event, muid, id)
            context.actorOf(Props(classOf[LibpfmPicker], libpfmHelper, event, index, Some(id), configuration), name)
        }

        sense(initIdentifiers)
      }
    }

    else {
      unsubscribeMonitorTick(muid, target)(eventBus)(self)
      self ! PoisonPill
      sensorDefault
    }
  }

  def sense(oldIdentifiers: Set[Int]): Actor.Receive = {
    case msg: MonitorTick =>
      val newIdentifiers = currentIdentifiers

      (oldIdentifiers -- newIdentifiers).foreach(id => context.actorSelection(s"*_${id}_*") ! LibpfmPickerStop)

      if (newIdentifiers.isEmpty) {
        unsubscribeMonitorTick(muid, target)(eventBus)(self)
        self ! PoisonPill
      }
      else {
        val combinations = {
          for {
            core: Int <- topology.keys
            index: Int <- topology(core)
            event: String <- events
            id: Int <- newIdentifiers
          } yield (core, index, event, id)
        }

        val allValues = combinations.map {
          case (core, index, event, id) =>
            val name = formatLibpfmCoreProcessSensorChildName(index, event, muid, id)
            val actor = context.child(name) match {
              case Some(ref) => ref
              case _ => context.actorOf(Props(classOf[LibpfmPicker], libpfmHelper, event, index, Some(id), configuration), name)
            }
            (core, event, Await.result(actor.?(msg.tick)(timeout), timeout.duration).asInstanceOf[HWCounter])
        }

        publishPCReport(muid, target, allValues.groupBy(_._1).mapValues(_.map { case (_, event, hpc) => Map(event -> hpc) }.reduce(_ ++ _)), msg.tick)(eventBus)

        context.become(sense(newIdentifiers) orElse sensorDefault)
      }
  }
}
