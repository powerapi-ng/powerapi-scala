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
import org.powerapi.core.MessageBus
import org.powerapi.module.SensorComponent

/**
 * Main actor for getting the performance counter value per core/event.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class LibpfmCoreSensor(eventBus: MessageBus, timeout: Timeout, configuration: BitSet, events: Array[String], cores: Map[Int, List[Int]]) extends SensorComponent(eventBus) {
  import akka.actor.{ActorIdentity, Identify, Props}
  import akka.pattern.ask
  import org.powerapi.core.All
  import org.powerapi.core.MonitorChannel.MonitorTick
  import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll, subscribeSensorsChannel}
  import PerformanceCounterChannel.{formatLibpfmCoreSensorChildName, PCWrapper, publishPCReport}
  import scala.concurrent.{Await, Future}

  override def preStart(): Unit = {
    subscribeSensorsChannel(eventBus)(self)
    super.preStart()
  }

  def sense(monitorTick: MonitorTick): Unit = {
    monitorTick.target match {
      case All => {
        var wrappers = Map[(Int, String), PCWrapper]()

        cores.foreach {
          case (core, indexes) => {
            indexes.foreach(index => {
              events.foreach(event => {
                val name = formatLibpfmCoreSensorChildName(index, event, monitorTick.muid)
                val identity = Await.result(context.actorSelection(name).?(Identify(None))(timeout), timeout.duration).asInstanceOf[ActorIdentity]

                val actor = identity.ref match {
                  case None => {
                    context.actorOf(Props(classOf[LibpfmCoreSensorChild], event, index, None, configuration), name)
                  }
                  case Some(ref) => ref
                }

                wrappers += (core, event) -> (wrappers.getOrElse((core, event), PCWrapper(core, event, List())) + actor.?(monitorTick)(timeout).asInstanceOf[Future[Long]])
              })
            })
          }
        }

        publishPCReport(monitorTick.muid, monitorTick.target, wrappers.values.toList, monitorTick.tick)(eventBus)
      }
      case _ => log.debug("Only the All target is handled by this sensor")
    }
  }

  def monitorStopped(msg: MonitorStop): Unit = {
    context.actorSelection(s"*${msg.muid}") ! msg
  }

  def monitorAllStopped(msg: MonitorStopAll): Unit = {
    context.actorSelection("*") ! msg
  }
}
