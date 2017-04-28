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
package org.powerapi.module.hwc

import java.util.UUID

import akka.actor.Actor
import com.twitter.util.Return
import com.twitter.zk.{ZNode, ZkClient}
import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.core.target.{All, Target}
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.hwc.HWCChannel.{HWCReport, subscribeHWCReport, unsubscribeHWCReport}

import scala.concurrent.duration.FiniteDuration

/**
  * Formula that uses cycles and ref_cycles to compute the power estimation.
  * ref_cycles is only used to compute the frequency coefficient.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class HWCCoreFormula(eventBus: MessageBus, muid: UUID, target: Target, likwidHelper: LikwidHelper, zkClient: ZkClient) extends Formula(eventBus, muid, target) {

  def init(): Unit = subscribeHWCReport(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeHWCReport(muid, target)(eventBus)(self)

  def handler: Actor.Receive = {
    val ModelRegex = ".+\\s+(.+)(?:\\s+v?\\d?)\\s+@.+".r
    val cpuModel = likwidHelper.getCpuInfo().osname match {
      case ModelRegex(model) => model.toLowerCase
    }
    val zkNodeModel = zkClient(s"/$cpuModel")
    val monitoring = zkNodeModel.getData.monitor()

    monitoring.foreach {
      case Return(node) =>
        node.getData() onSuccess {
          case data: ZNode.Data =>
            context.become(compute(new String(data.bytes).split(",").map(_.toDouble)) orElse formulaDefault)
        }
      case _ =>

    }

    compute(Seq(0.0, 0.0, 0.0))
  }

  def compute(formula: Seq[Double]): Actor.Receive = {
    case msg: HWCReport =>
      //val now = System.nanoTime()

      val powers = for ((core, hwcs) <- msg.values.groupBy(_.hwThread.coreId)) yield {
        val cycles = hwcs.filter(_.event == "CPU_CLK_UNHALTED_CORE:FIXC1").foldLeft(0d)((acc, hwc) => hwc.value)
        if(target == All) println(s"$core, $cycles")
        /*val cyclesVal = cycles.map(_.value).sum
        val scaledCycles = cyclesVal*/
        //val scaledCycles = if (now - old <= 0) 0l else math.round(cyclesVal * (samplingInterval.toNanos / (now - old).toDouble))
        //val refs = hwcs.filter(_.event.startsWith("CPU_CLK_UNHALTED_REF"))
        //val refsVal = refs.map(_.value).sum
        //val scaledRefs = if (now - old <= 0) 0l else math.round(refsVal * (samplingInterval.toNanos / (now - old).toDouble))

        /*var coefficient: Double = math.round(scaledCycles / scaledRefs.toDouble)

        if (coefficient.isNaN || coefficient < formulae.keys.min) coefficient = formulae.keys.min

        if (coefficient > formulae.keys.max) coefficient = formulae.keys.max

        if (!formulae.contains(coefficient)) {
          val coefficientsBefore = formulae.keys.filter(_ < coefficient)
          coefficient = coefficientsBefore.max
        }*/

        val formulaWoIdle = formula.updated(0, 0d)
        val power = formulaWoIdle.zipWithIndex.foldLeft(0d)((acc, tuple) => acc + (tuple._1 * math.pow(cycles, tuple._2)))
        println(formulaWoIdle)
        println(power)
        power
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

      if (target == All) println(powers)
      if(target == All) println("=====")

      publishRawPowerReport(muid, target, accPower, "cpu", msg.tick)(eventBus)
  }
}
