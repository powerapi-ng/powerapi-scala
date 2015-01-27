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

import org.powerapi.configuration.{TopologyConfiguration, TimeoutConfiguration}
import org.powerapi.core.{Configuration, MessageBus}
import org.powerapi.module.SensorComponent
import scala.collection.BitSet

/**
 * Main configuration for LibpfmCore sensors.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
trait LibpfmCoreConfiguration extends Configuration {
  import org.powerapi.core.ConfigValue

  /**
   * List of enabled bits for the perf_event_open maccro.
   * The bits to configure are described in the structure perf_event_attr available below.
   *
   * @see http://manpages.ubuntu.com/manpages/trusty/en/man2/perf_event_open.2.html
   */
  lazy val configuration =
    BitSet(
      (load { _.getIntList("powerapi.libpfm.configuration") } match {
        case ConfigValue(values) => values.asInstanceOf[List[Int]]
        case _ => List[Int]()
      }): _*
    )

  lazy val events = load { _.getStringList("powerapi.libpfm.events") } match {
    case ConfigValue(values) => values.asInstanceOf[List[String]]
    case _ => List()
  }
}

/**
 * Main actor for getting the performance counter value per core/event.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmCoreSensor(eventBus: MessageBus) extends SensorComponent(eventBus) with TimeoutConfiguration with TopologyConfiguration with LibpfmCoreConfiguration {
  import akka.actor.Props
  import akka.pattern.ask
  import org.powerapi.core.MonitorChannel.MonitorTick
  import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll, subscribeSensorsChannel}
  import PerformanceCounterChannel.{formatLibpfmCoreSensorChildName, PCWrapper, publishPCReport}
  import scala.concurrent.Future

  override def preStart(): Unit = {
    subscribeSensorsChannel(eventBus)(self)
    super.preStart()
  }

  def sense(monitorTick: MonitorTick): Unit = {
    var wrappers = Map[(Int, String), PCWrapper]()

    topology.foreach {
      case (core, indexes) => {
        indexes.foreach(index => {
          events.foreach(event => {
            val name = formatLibpfmCoreSensorChildName(index, event, monitorTick.muid)

            val actor = context.child(name) match {
              case Some(ref) => ref
              case None => context.actorOf(Props(classOf[LibpfmCoreSensorChild], event, index, None, configuration), name)
            }

            wrappers += (core, event) -> (wrappers.getOrElse((core, event), PCWrapper(core, event, List())) + actor.?(monitorTick)(timeout).asInstanceOf[Future[Long]])
          })
        })
      }
    }

    publishPCReport(monitorTick.muid, monitorTick.target, wrappers.values.toList, monitorTick.tick)(eventBus)
  }

  def monitorStopped(msg: MonitorStop): Unit = {
    context.actorSelection(s"*${msg.muid}") ! msg
  }

  def monitorAllStopped(msg: MonitorStopAll): Unit = {
    context.actorSelection("*") ! msg
  }
}
