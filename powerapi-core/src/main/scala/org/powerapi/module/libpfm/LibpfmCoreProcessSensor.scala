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

import akka.actor.{Actor, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import java.util.UUID
import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick}
import org.powerapi.core.target.{Application, Process, Target}
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll, subscribeSensorsChannel}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{formatLibpfmCoreProcessSensorChildName, PCWrapper, publishPCReport}
import org.powerapi.core.{OSHelper, APIComponent, MessageBus}
import scala.collection.BitSet
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * Main actor for getting the performance counter value per core/event/process.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmCoreProcessSensor(eventBus: MessageBus, osHelper: OSHelper, timeout: Timeout, topology: Map[Int, Iterable[Int]], configuration: BitSet, events: List[String], inDepth: Boolean) extends APIComponent {
  val processClaz = implicitly[ClassTag[Process]].runtimeClass
  val appClaz = implicitly[ClassTag[Application]].runtimeClass

  override def preStart(): Unit = {
    subscribeMonitorTick(eventBus)(self)
    subscribeSensorsChannel(eventBus)(self)
    super.preStart()
  }

  def receive: Actor.Receive = running(Map(), Map(), Map())

  def running(targets: Map[UUID, Set[Target]], timestamps: Map[UUID, Long], identifiers: Map[(UUID, Target), Set[Int]]): Actor.Receive = LoggingReceive {
    case monitorTick: MonitorTick if processClaz.isInstance(monitorTick.target) || appClaz.isInstance(monitorTick.target) => {
      sense(monitorTick, targets, timestamps, identifiers)
    }
    case msg: MonitorStop => monitorStopped(msg, targets, timestamps, identifiers)
    case msg: MonitorStopAll => monitorAllStopped(msg, targets, timestamps, identifiers)
  } orElse default

  def sense(monitorTick: MonitorTick, targets: Map[UUID, Set[Target]], timestamps: Map[UUID, Long], identifiers: Map[(UUID, Target), Set[Int]]): Unit = {
    var _targets = targets
    var _timestamps = timestamps
    var _identifiers = identifiers
    var wrappers = Map[(Int, String), PCWrapper]()

    if(!_timestamps.contains(monitorTick.muid)) {
      _timestamps += monitorTick.muid -> monitorTick.tick.timestamp
    }

    /**
     * Clean the resources for the old targets (for a given muid)
     */
    if(monitorTick.tick.timestamp > _timestamps(monitorTick.muid)) {
      _identifiers.filter(entry => entry._1._1 == monitorTick.muid && !_targets(monitorTick.muid).contains(entry._1._2)).foreach {
        case (key, ids) => {
          ids.foreach(id => context.actorSelection(s"*$id") ! PoisonPill)
          _identifiers -= key
        }
      }

      _targets += monitorTick.muid -> Set()
      _timestamps += monitorTick.muid -> monitorTick.tick.timestamp
    }

    _targets += monitorTick.muid -> (_targets.getOrElse(monitorTick.muid, Set()) + monitorTick.target)

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

    val newTickIdentifiers = tickIdentifiers -- _identifiers.getOrElse((monitorTick.muid, monitorTick.target), Set())
    val oldTickIdentifiers = _identifiers.getOrElse((monitorTick.muid, monitorTick.target), Set()) -- tickIdentifiers

    /**
     * Clean the resources for the old identifiers linked to the given target.
     */
    oldTickIdentifiers.foreach(id => {
      context.actorSelection(s"*$id") ! PoisonPill
    })

    _identifiers += (monitorTick.muid, monitorTick.target) -> (_identifiers.getOrElse((monitorTick.muid, monitorTick.target), Set()) -- oldTickIdentifiers ++ newTickIdentifiers)

    /** Actors were not created before */
    topology.foreach {
      case (core, indexes) => {
        indexes.foreach(index => {
          events.foreach(event => {
            _identifiers(monitorTick.muid, monitorTick.target).foreach(id => {
              val name = formatLibpfmCoreProcessSensorChildName(index, event, monitorTick.muid, id)

              val actor = context.child(name) match {
                case Some(ref) => ref
                case None => context.actorOf(Props(classOf[LibpfmCoreSensorChild], event, index, Some(id), configuration), name)
              }

              wrappers += (core, event) -> (wrappers.getOrElse((core, event), PCWrapper(core, event, List())) + actor.?(monitorTick)(timeout).asInstanceOf[Future[Long]])
            })
          })
        })
      }
    }

    publishPCReport(monitorTick.muid, monitorTick.target, wrappers.values.toList, monitorTick.tick)(eventBus)
    context.become(running(_targets, _timestamps, _identifiers))
  }

  def monitorStopped(msg: MonitorStop, targets: Map[UUID, Set[Target]], timestamps: Map[UUID, Long], identifiers: Map[(UUID, Target), Set[Int]]): Unit = {
    var _targets = targets
    var _timestamps = timestamps
    var _identifiers = identifiers

    context.actorSelection(s"*${msg.muid}*") ! msg
    _targets --= _targets.keys.filter(muid => muid == msg.muid)
    _timestamps --= _timestamps.keys.filter(muid => muid == msg.muid)
    _identifiers --= _identifiers.keys.filter(key => key._1 == msg.muid)

    context.become(running(_targets, _timestamps, _identifiers))
  }

  def monitorAllStopped(msg: MonitorStopAll, targets: Map[UUID, Set[Target]], timestamps: Map[UUID, Long], identifiers: Map[(UUID, Target), Set[Int]]): Unit = {
    context.actorSelection("*") ! msg

    context.become(running(Map(), Map(), Map()))
  }
}
