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
package org.powerapi.module.libpfm

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import akka.actor.Actor

import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.libpfm.PerformanceCounterChannel.{PCReport, subscribePCReport, unsubscribePCReport}

/**
  * This formula is designed to fit a multivariate power model.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class LibpfmFormula(eventBus: MessageBus, muid: UUID, target: Target, formula: Map[String, Double], samplingInterval: FiniteDuration)
  extends Formula(eventBus, muid, target) {

  def init(): Unit = subscribePCReport(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribePCReport(muid, target)(eventBus)(self)

  def handler: Actor.Receive = compute(System.nanoTime())

  def compute(old: Long): Actor.Receive = {
    case msg: PCReport =>
      val now = System.nanoTime()

      val powers = for ((event, coeff) <- formula) yield {
        if (now - old <= 0) 0
        else {
          val value = msg.values.values.flatten.collect {
            case (ev, counters) if ev == event => counters.map(_.value)
          }.foldLeft(Seq[Long]())((acc, value) => acc ++ value).sum

          coeff * math.round(value * (samplingInterval.toNanos / (now - old).toDouble))
        }
      }

      publishRawPowerReport(muid, target, powers.sum.W, "cpu", msg.tick)(eventBus)

      context.become(compute(now) orElse formulaDefault)
  }
}
