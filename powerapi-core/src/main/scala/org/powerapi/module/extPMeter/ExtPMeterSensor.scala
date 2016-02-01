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
package org.powerapi.module.extPMeter

import akka.event.LoggingReceive
import org.powerapi.core.{ExternalPMeter, MessageBus, APIComponent}
import org.powerapi.module.extPMeter.ExtPMeterChannel.{ExtPMeterPower, publishPMeterPower, subscribeExternalPMeterPower}

/**
 * ExtPMeterSensor's implementation by using an helper.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
class ExtPMeterSensor(eventBus: MessageBus, pMeter: ExternalPMeter) extends APIComponent {

  override def preStart(): Unit = {
    subscribeExternalPMeterPower(eventBus)(self)
    pMeter.init(eventBus)
    pMeter.start()
    super.preStart()
  }

  override def postStop(): Unit = {
    pMeter.stop()
    super.postStop()
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: ExtPMeterPower => sense(msg)
  } orElse default

  def sense(epmPower: ExtPMeterPower): Unit = {
    publishPMeterPower(epmPower.power)(eventBus)
  }
}
