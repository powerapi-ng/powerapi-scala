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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

import akka.actor.Actor

import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, PCReport, subscribePCReport, unsubscribePCReport}

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

      val scaledCounters: Future[Iterable[(String, Long)]] = Future.sequence {
        for ((event, wrappers) <- msg.wrappers.groupBy(_.event)) yield {
          Future.sequence(wrappers.flatMap(_.values)).map {
            case counters: Seq[HWCounter] =>

              event -> {
                if (now - old <= 0) 0l else math.round(counters.map(_.value).sum * (samplingInterval.toNanos / (now - old).toDouble))
              }
          }
        }
      }

      scaledCounters onComplete {
        case Success(counters: Iterable[(String, Long)]) =>
          val power = (for (tuple <- counters) yield formula.getOrElse(tuple._1, 0d) * tuple._2).sum
          publishRawPowerReport(muid, target, if (power > 0) power.W else 0.W, "cpu", msg.tick)(eventBus)

        case Failure(ex) =>
          log.warning("An error occurred: {}", ex.getMessage)
          publishRawPowerReport(muid, target, 0.W, "cpu", msg.tick)(eventBus)
      }

      context.become(compute(now) orElse formulaDefault)
  }
}
