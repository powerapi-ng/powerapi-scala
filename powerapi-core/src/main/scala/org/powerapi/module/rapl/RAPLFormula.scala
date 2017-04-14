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
package org.powerapi.module.rapl

import java.util.UUID

import akka.actor.Actor
import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.hwc.RAPLDomain.RAPLDomain
import org.powerapi.module.rapl.RAPLChannel.{RAPLReport, subscribeRAPLReport, unsubscribeRAPLReport}

import scala.concurrent.duration.DurationLong

/**
  * RAPL Formula for the All target.
  * TODO: other targets.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class RAPLFormula(eventBus: MessageBus, muid: UUID, target: Target, domain: RAPLDomain) extends Formula(eventBus, muid, target) {

  def init(): Unit = subscribeRAPLReport(muid, target, domain)(eventBus)(self)

  def terminate(): Unit = unsubscribeRAPLReport(muid, target, domain)(eventBus)(self)

  def handler: Actor.Receive = compute(System.nanoTime())

  def compute(old: Long): Actor.Receive = {
    case msg: RAPLReport =>
      val now = System.nanoTime()

      val power = msg.energies.sum * (1.seconds.toNanos / (now - old).toDouble)

      publishRawPowerReport(muid, target, power.W, domain.toString(), msg.tick)(eventBus)
      context.become(compute(now) orElse formulaDefault)
  }
}
