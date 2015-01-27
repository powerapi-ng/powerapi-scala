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

import java.util.BitSet
import akka.util.Timeout
import org.powerapi.core.{APIComponent, MessageBus}

/**
 * Main actor for getting the performance counter value per core/event.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class LibpfmCoreSensor(eventBus: MessageBus, timeout: Timeout, configuration: BitSet, events: Array[String], cores: Map[Int, List[Int]]) extends APIComponent {
  import akka.actor.{Actor, ActorRef, Props}
  import akka.event.LoggingReceive
  import akka.pattern.ask
  import java.util.UUID
  import org.powerapi.core.target.All
  import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick}
  import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll, subscribeSensorsChannel}
  import PerformanceCounterChannel.{formatLibpfmCoreSensorChildName, PCWrapper, publishPCReport}
  import scala.concurrent.Future

  override def preStart(): Unit = {
    subscribeMonitorTick(eventBus)(self)
    subscribeSensorsChannel(eventBus)(self)
    super.preStart()
  }

  def receive: Actor.Receive = running(Map())

  def running(actors: Map[(Int, String, UUID), ActorRef]): Actor.Receive = LoggingReceive {
    case monitorTick: MonitorTick if monitorTick.target == All => sense(monitorTick, actors)
    case msg: MonitorStop => monitorStopped(msg, actors)
    case msg: MonitorStopAll => monitorAllStopped(msg, actors)
  } orElse default

  def sense(monitorTick: MonitorTick, actors: Map[(Int, String, UUID), ActorRef]): Unit = {
    var _actors = actors
    var wrappers = Map[(Int, String), PCWrapper]()

    cores.foreach {
      case (core, indexes) => {
        indexes.foreach(index => {
          events.foreach(event => {
            val actor = {
              /** Actors were not created before */
              if(!actors.contains(index, event, monitorTick.muid)) {
                val name = formatLibpfmCoreSensorChildName(index, event, monitorTick.muid)
                val actor = context.actorOf(Props(classOf[LibpfmCoreSensorChild], event, index, None, configuration), name)
                _actors += ((index, event, monitorTick.muid) -> actor)
                actor
              }

              else _actors(index, event, monitorTick.muid)
            }

            wrappers += (core, event) -> (wrappers.getOrElse((core, event), PCWrapper(core, event, List())) + actor.?(monitorTick)(timeout).asInstanceOf[Future[Long]])
          })
        })
      }
    }

    publishPCReport(monitorTick.muid, monitorTick.target, wrappers.values.toList, monitorTick.tick)(eventBus)
    context.become(running(_actors))
  }

  def monitorStopped(msg: MonitorStop, actors: Map[(Int, String, UUID), ActorRef]): Unit = {
    var _actors = actors

    context.actorSelection(s"*${msg.muid}") ! msg
    _actors --= _actors.keys.filter(key => key._3 == msg.muid)

    context.become(running(_actors))
  }

  def monitorAllStopped(msg: MonitorStopAll, actors: Map[(Int, String, UUID), ActorRef]): Unit = {
    context.actorSelection("*") ! msg

    context.become(running(Map()))
  }
}