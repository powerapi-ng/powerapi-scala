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
package org.powerapi.module.libpfm.cycles

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import akka.actor.Actor

import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, PCReport, subscribePCReport, unsubscribePCReport}

/**
  * Special implementation of Libpfm Formula.
  * This formula used two events: CPU_CLK_UNHALTED:THREAD_P and CPU_CLK_UNHALTED:REF_P.
  * The first counter is injected for computing the power and the second one for the frequency's coefficient.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class LibpfmCoreCyclesFormula(eventBus: MessageBus, muid: UUID, target: Target, cyclesThreadName: String, cyclesRefName: String, formulae: Map[Double, List[Double]], samplingInterval: FiniteDuration)
  extends Formula(eventBus, muid, target) {

  def init(): Unit = subscribePCReport(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribePCReport(muid, target)(eventBus)(self)

  def handler: Actor.Receive = compute(System.nanoTime())

  def compute(old: Long): Actor.Receive = {
    case msg: PCReport =>
      val now = System.nanoTime()

      val powers = for (value <- msg.values) yield {
        val cycles = value._2.getOrElse(cyclesThreadName, Seq(HWCounter(0)))
        val refs = value._2.getOrElse(cyclesRefName, Seq(HWCounter(0)))
        val cyclesVal = cycles.map(_.value).sum
        val scaledCycles = if (now - old <= 0) 0l else math.round(cyclesVal * (samplingInterval.toNanos / (now - old).toDouble))

        val refsVal = refs.map(_.value).sum
        val scaledRefs = if (now - old <= 0) 0l else math.round(refsVal * (samplingInterval.toNanos / (now - old).toDouble))

        var coefficient: Double = math.round(scaledCycles / scaledRefs.toDouble)

        if (coefficient.isNaN || coefficient < formulae.keys.min) coefficient = formulae.keys.min

        if (coefficient > formulae.keys.max) coefficient = formulae.keys.max

        if (!formulae.contains(coefficient)) {
          val coefficientsBefore = formulae.keys.filter(_ < coefficient)
          coefficient = coefficientsBefore.max
        }

        val formula = formulae(coefficient).updated(0, 0d)
        formula.zipWithIndex.foldLeft(0d)((acc, tuple) => acc + (tuple._1 * math.pow(scaledCycles, tuple._2)))
      }

      val accPower = {
        try {
          powers.sum.W
        }
        catch {
          case _: Exception =>
            log.warning("The power value is out of range. Skip.")
            0.W
        }
      }

      publishRawPowerReport(muid, target, accPower, "cpu", msg.tick)(eventBus)
      context.become(compute(now) orElse formulaDefault)
  }
}
