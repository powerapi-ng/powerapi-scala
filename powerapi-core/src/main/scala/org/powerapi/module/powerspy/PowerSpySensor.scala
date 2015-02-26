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
package org.powerapi.module.powerspy

import akka.event.LoggingReceive
import org.powerapi.core.{ExternalPMeter, MessageBus, APIComponent}
import org.powerapi.module.powerspy.PowerSpyChannel.{PowerSpyPower, publishPowerSpyPower, subscribeExternalPowerSpyPower}

/**
 * PowerSpySensor's implementation by using an helper.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class PowerSpySensor(eventBus: MessageBus, pMeter: ExternalPMeter) extends APIComponent {

  override def preStart(): Unit = {
    subscribeExternalPowerSpyPower(eventBus)(self)
    pMeter.init()
    pMeter.start()
    super.preStart()
  }

  override def postStop(): Unit = {
    pMeter.stop()
    super.postStop()
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: PowerSpyPower => sense(msg)
  } orElse default

  def sense(pSpyPower: PowerSpyPower): Unit = {
    publishPowerSpyPower(pSpyPower.power)(eventBus)
  }
}
