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
package org.powerapi.module

import akka.event.LoggingReceive
import org.powerapi.core.{APIComponent, MessageBus}
import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick}
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll, subscribeSensorsChannel}

/**
 * Base trait for each PowerAPI sensor.
 * Each of them should react to a MonitorTick, sense informations and then publish a SensorReport.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
abstract class SensorComponent(eventBus: MessageBus) extends APIComponent {

  override def preStart(): Unit = {
    subscribeMonitorTick(eventBus)(self)
    subscribeSensorsChannel(eventBus)(self)
    super.preStart()
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: MonitorTick => sense(msg)
    case msg: MonitorStop => monitorStopped(msg)
    case msg: MonitorStopAll => monitorAllStopped(msg)
  } orElse default

  def sense(monitorTick: MonitorTick): Unit
  def monitorStopped(msg: MonitorStop): Unit
  def monitorAllStopped(msg: MonitorStopAll)
}
