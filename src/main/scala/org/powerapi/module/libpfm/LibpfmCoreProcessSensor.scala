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

import java.util.{BitSet, UUID}
import akka.util.Timeout
import org.powerapi.core.{OSHelper, APIComponent, MessageBus, Target}

/**
 * Main actor for getting the performance counter value per core/event/process.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class LibpfmCoreProcessSensor(eventBus: MessageBus, timeout: Timeout, osHelper: OSHelper, configuration: BitSet, events: Array[String], cores: Map[Int, List[Int]], inDepth: Boolean) extends APIComponent {
  import akka.actor.{Actor, ActorIdentity, Identify, Props}
  import akka.event.LoggingReceive
  import akka.pattern.ask
  import org.powerapi.core.{Application, Process}
  import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick}
  import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll, subscribeSensorsChannel}
  import PerformanceCounterChannel.{CleanResource, formatLibpfmCoreProcessSensorChildName, PCWrapper, publishPCReport}
  import scala.concurrent.{Await, Future}
  import scala.reflect.ClassTag

  override def preStart(): Unit = {
    subscribeMonitorTick(eventBus)(self)
    subscribeSensorsChannel(eventBus)(self)
    super.preStart()
  }

  val processClaz = implicitly[ClassTag[Process]].runtimeClass
  val appClaz = implicitly[ClassTag[Application]].runtimeClass

  def receive: Actor.Receive = running(Map(), Map(), Map())

  def running(targets: Map[UUID, Set[Target]], timestamps: Map[UUID, Long], identifiers: Map[(UUID, Target), Set[Int]]): Actor.Receive = LoggingReceive {
    case monitorTick: MonitorTick if processClaz.isInstance(monitorTick.target) || appClaz.isInstance(monitorTick.target) => {
      sense(monitorTick, targets, timestamps, identifiers)
    }
    case msg: MonitorStop => monitorStopped(msg)
    case msg: MonitorStopAll => monitorAllStopped(msg)
  } orElse default

  def sense(monitorTick: MonitorTick, targets: Map[UUID, Set[Target]], timestamps: Map[UUID, Long], identifiers: Map[(UUID, Target), Set[Int]]): Unit = {
    var wrappers = Map[(Int, String), PCWrapper]()

    var _targets = targets
    var _timestamps = timestamps
    var _identifiers = identifiers

    if(!_timestamps.contains(monitorTick.muid)) {
      _timestamps += monitorTick.muid -> monitorTick.tick.timestamp
    }

    /**
     * Clean the resources for the old targets (for a given muid)
     */
    if(monitorTick.tick.timestamp > _timestamps(monitorTick.muid)) {
      _identifiers.filter(entry => entry._1._1 == monitorTick.muid && !_targets(monitorTick.muid).contains(entry._1._2)).foreach {
        case (key, ids) => {
          ids.foreach(id => context.actorSelection(s"*$id") ! CleanResource)
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
          if(inDepth) osHelper.getThreads(process).map(_.tid) :+ process.pid
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
      context.actorSelection(s"*$id") ! CleanResource
    })

    _identifiers += (monitorTick.muid, monitorTick.target) -> (_identifiers.getOrElse((monitorTick.muid, monitorTick.target), Set()) -- oldTickIdentifiers ++ newTickIdentifiers)

    cores.foreach {
      case (core, indexes) => {
        indexes.foreach(index => {
          events.foreach(event => {
            _identifiers(monitorTick.muid, monitorTick.target).foreach(id => {
              val name = formatLibpfmCoreProcessSensorChildName(index, event, monitorTick.muid, id)
              val identity = Await.result(context.actorSelection(name).?(Identify(None))(timeout), timeout.duration).asInstanceOf[ActorIdentity]

              val actor = identity.ref match {
                case None => {
                  context.actorOf(Props(classOf[LibpfmCoreSensorChild], event, index, Some(id), configuration), name)
                }
                case Some(ref) => ref
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

  def monitorStopped(msg: MonitorStop): Unit = {
    context.actorSelection(s"*${msg.muid}*") ! msg
  }

  def monitorAllStopped(msg: MonitorStopAll): Unit = {
    context.actorSelection("*") ! msg
  }
}
