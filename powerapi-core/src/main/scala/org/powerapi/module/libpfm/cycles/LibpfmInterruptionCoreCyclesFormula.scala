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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.actor.Actor

import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.libpfm.PCInterruptionChannel.{InterruptionTick, InterruptionPCReport, subscribeInterruptionPCReport, unsubscribeInterruptionPCReport}
import org.powerapi.module.libpfm.TID

/**
  * Special implementation of power model, using two named events respectively, "unhalted cycles" and "reference cycles".
  * The first one increases following the load, and the second one is used to compute running frequency of the CPU.
  * One RawPower message is published per Thread ID, which corresponds to a running method).
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class LibpfmInterruptionCoreCyclesFormula(eventBus: MessageBus, muid: UUID, target: Target, cyclesThreadName: String, cyclesRefName: String, formulae: Map[Double, List[Double]], samplingInterval: FiniteDuration)
  extends Formula(eventBus, muid, target) {

  def init(): Unit = subscribeInterruptionPCReport(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeInterruptionPCReport(muid, target)(eventBus)(self)

  def handler: Actor.Receive = compute(System.nanoTime())

  def compute(old: Long): Actor.Receive = {
    case msg: InterruptionPCReport =>
      val now = System.nanoTime()

      for ((_, wrappers) <- msg.wrappers.groupBy(_.core)) {
        if (wrappers.count(_.event == cyclesThreadName) == 1 || wrappers.count(_.event == cyclesRefName) == 1) {
          val cyclesThread = wrappers.filter(_.event == cyclesThreadName).head.values
          val cyclesRef = wrappers.filter(_.event == cyclesRefName).head.values

          val cyclesVal = cyclesThread.map(_.value).sum
          val scaledCycles = if (now - old <= 0) 0l else math.round(cyclesVal * (samplingInterval.toNanos / (now - old).toDouble))

          val refsVal = cyclesRef.map(_.value).sum
          val scaledRefs = if (now - old <= 0) 0l else math.round(refsVal * (samplingInterval.toNanos / (now - old).toDouble))

          var coefficient: Double = math.round(scaledCycles / scaledRefs.toDouble)

          if (coefficient.isNaN || coefficient < formulae.keys.min) coefficient = formulae.keys.min

          if (coefficient > formulae.keys.max) coefficient = formulae.keys.max

          if (!formulae.contains(coefficient)) {
            val coefficientsBefore = formulae.keys.filter(_ < coefficient)
            coefficient = coefficientsBefore.max
          }

          val formula = formulae(coefficient).updated(0, 0d)
          val corePower = formula.zipWithIndex.foldLeft(0d)((acc, tuple) => acc + (tuple._1 * math.pow(scaledCycles, tuple._2)))

          cyclesThread.foreach {
            case cycles =>
              val threadPower = corePower * (cycles.value / cyclesVal.toDouble)
              val tick = InterruptionTick("", cycles.cpu, TID(cycles.tid), cycles.fullMethodName, msg.tick.timestamp, cycles.triggering)
              publishRawPowerReport(muid, target, if (threadPower > 0) threadPower.W else 0.W, "cpu", tick)(eventBus)
          }

          context.become(compute(now) orElse formulaDefault)
        }
      }
  }
}
