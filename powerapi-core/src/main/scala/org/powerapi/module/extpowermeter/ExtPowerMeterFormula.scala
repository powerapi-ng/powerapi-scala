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
package org.powerapi.module.extpowermeter

import java.util.UUID

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive

import org.powerapi.core.MessageBus
import org.powerapi.core.target.Target
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.extpowermeter.ExtPowerMeterChannel.ExtPowerMeterPowerReport

/**
  * Base implementation for external power meter formulae.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
abstract class ExtPowerMeterFormula(eventBus: MessageBus, muid: UUID, target: Target,
                                    subscribeExtPowerReport: (UUID, Target) => MessageBus => ActorRef => Unit,
                                    unsubscribeExtPowerReport: (UUID, Target) => MessageBus => ActorRef => Unit) extends Formula(eventBus, muid, target) {

  def init(): Unit = {
    subscribeExtPowerReport(muid, target)(eventBus)(self)
  }

  def terminate(): Unit = {
    unsubscribeExtPowerReport(muid, target)(eventBus)(self)
  }

  def handler: Actor.Receive = LoggingReceive {
    case msg: ExtPowerMeterPowerReport =>
      val power = msg.power * msg.targetRatio.ratio
      publishRawPowerReport(msg.muid, msg.target, power, msg.source, msg.tick)(eventBus)
  }
}
