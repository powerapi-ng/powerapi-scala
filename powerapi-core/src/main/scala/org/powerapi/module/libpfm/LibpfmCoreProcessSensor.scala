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

import akka.actor.{PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import java.util.UUID
import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick}
import org.powerapi.core.target.{Application, Process, Target}
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll, subscribeSensorsChannel}
import org.powerapi.module.SensorComponent
import org.powerapi.module.libpfm.PerformanceCounterChannel.{formatLibpfmCoreProcessSensorChildName, PCWrapper, publishPCReport}
import org.powerapi.core.{OSHelper, MessageBus}
import scala.collection.BitSet
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * Main actor for getting the performance counter value per core/event/process.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmCoreProcessSensor(eventBus: MessageBus, osHelper: OSHelper, libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], configuration: BitSet, events: Set[String], inDepth: Boolean) extends SensorComponent(eventBus) {
  val processClaz = implicitly[ClassTag[Process]].runtimeClass
  val appClaz = implicitly[ClassTag[Application]].runtimeClass

  val wrappers = scala.collection.mutable.Map[(Int, String), PCWrapper]()
  val targets = scala.collection.mutable.Map[UUID, Set[Target]]()
  val timestamps = scala.collection.mutable.Map[UUID, Long]()
  val identifiers = scala.collection.mutable.Map[(UUID, Target), Set[Int]]()

  override def preStart(): Unit = {
    subscribeMonitorTick(eventBus)(self)
    subscribeSensorsChannel(eventBus)(self)
    super.preStart()
  }

  def sense(monitorTick: MonitorTick): Unit = {
    if(!timestamps.contains(monitorTick.muid)) {
      timestamps += monitorTick.muid -> monitorTick.tick.timestamp
    }

    /**
     * Get the identifiers for the current target.
     */
    val tickIdentifiers: Set[Int] = monitorTick.target match {
      case process: Process => {
        if(inDepth) osHelper.getThreads(process).map(_.tid).toSet + process.pid
        else Set(process.pid)
      }
      case app: Application => {
        (for(process <- osHelper.getProcesses(app)) yield {
          if(inDepth) osHelper.getThreads(process).map(_.tid) ++ Iterable(process.pid)
          else List(process.pid)
        }).flatten.toSet
      }
    }

    /**
     * Clean the resources for the old identifiers linked to the requested target.
     */
    val newTickIdentifiers = tickIdentifiers -- identifiers.getOrElse((monitorTick.muid, monitorTick.target), Set())
    val oldTickIdentifiers = identifiers.getOrElse((monitorTick.muid, monitorTick.target), Set()) -- tickIdentifiers

    oldTickIdentifiers.foreach(id => {
      context.actorSelection(s"*$id") ! PoisonPill
    })

    /**
     * Clean the resources for the old targets linked to the MUID.
     */
    if(monitorTick.tick.timestamp > timestamps(monitorTick.muid)) {
      identifiers.filter(entry => entry._1._1 == monitorTick.muid && !targets(monitorTick.muid).contains(entry._1._2)).foreach {
        case (key, ids) => {
          ids.foreach(id => context.actorSelection(s"*$id") ! PoisonPill)
          identifiers -= key
        }
      }

      targets += monitorTick.muid -> Set()
      timestamps += monitorTick.muid -> monitorTick.tick.timestamp
    }

    targets += monitorTick.muid -> (targets.getOrElse(monitorTick.muid, Set()) + monitorTick.target)
    identifiers += (monitorTick.muid, monitorTick.target) -> (identifiers.getOrElse((monitorTick.muid, monitorTick.target), Set()) -- oldTickIdentifiers ++ newTickIdentifiers)

    for((core, indexes) <- topology) {
      for(index <- indexes) {
        for(event <- events) {
          for(id <- identifiers(monitorTick.muid, monitorTick.target)) {
            val name = formatLibpfmCoreProcessSensorChildName(index, event, monitorTick.muid, id)

            val actor = context.child(name) match {
              case Some(ref) => ref
              case None => context.actorOf(Props(classOf[LibpfmCoreSensorChild], libpfmHelper, event, index, Some(id), configuration), name)
            }

            wrappers += (core, event) -> (wrappers.getOrElse((core, event), PCWrapper(core, event, List())) + actor.?(monitorTick)(timeout).asInstanceOf[Future[Long]])
          }
        }
      }
    }

    publishPCReport(monitorTick.muid, monitorTick.target, wrappers.values.toList, monitorTick.tick)(eventBus)
    wrappers.clear()
  }

  def monitorStopped(msg: MonitorStop): Unit = {
    context.actorSelection(s"*${msg.muid}*") ! msg
    targets --= targets.keys.filter(muid => muid == msg.muid)
    timestamps --= timestamps.keys.filter(muid => muid == msg.muid)
    identifiers --= identifiers.keys.filter(key => key._1 == msg.muid)
    wrappers.clear()
  }

  def monitorAllStopped(msg: MonitorStopAll): Unit = {
    context.actorSelection("*") ! msg
    targets.clear()
    timestamps.clear()
    identifiers.clear()
    wrappers.clear()
  }
}
