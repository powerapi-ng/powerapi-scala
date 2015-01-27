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
package org.powerapi.module.libpfm.cycles

import org.powerapi.configuration.SamplingConfiguration
import org.powerapi.core.{Configuration, MessageBus}
import org.powerapi.module.FormulaComponent
import org.powerapi.module.libpfm.PerformanceCounterChannel.PCReport

/**
 * Special implementation of Libpfm Formula.
 * Here, the formula used two events: CPU_CLK_UNHALTED:THREAD_P and CPU_CLK_UNHALTED:REF_P.
 * The formula file contains an equation of degree two.
 * The first counter is injected for computing the power.
 * The second one is used for computing the frequency coefficient.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class LibpfmCoreCyclesFormula(eventBus: MessageBus) extends FormulaComponent[PCReport](eventBus) with SamplingConfiguration with Configuration {
  import breeze.numerics.polyval
  import com.typesafe.config.Config
  import org.powerapi.core.ConfigValue
  import org.powerapi.module.libpfm.PerformanceCounterChannel.subscribePCReport
  import org.powerapi.module.PowerChannel.publishPowerReport
  import org.powerapi.module.PowerUnit
  import scala.collection.JavaConversions._
  import scala.concurrent.Future

  lazy val cyclesThreadName: String = load { _.getString("powerapi.libpfm.formulae.cycles-thread") } match {
    case ConfigValue(value) => value
    case _ => "CPU_CLK_UNHALTED:THREAD_P"
  }

  lazy val cyclesRefName: String = load { _.getString("powerapi.libpfm.formulae.cycles-ref") } match {
    case ConfigValue(value) => value
    case _ => "CPU_CLK_UNHALTED:REF_P"
  }

  lazy val formulae: Map[Double, Array[Double]] = load { conf =>
    (for (item: Config <- conf.getConfigList("powerapi.libpfm.formulae.cycles"))
      yield (item.getDouble("coefficient"), item.getDoubleList("formula").map(_.toDouble).toArray)).toMap
  } match {
    case ConfigValue(values) => values
    case _ => Map()
  }

  def subscribeSensorReport(): Unit = {
    subscribePCReport(eventBus)(self)
  }

  def compute(sensorReport: PCReport): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val computations: Iterable[Future[Double]] = for((_, wrappers) <- sensorReport.wrappers.groupBy(_.core)) yield {
      if(wrappers.count(_.event == cyclesThreadName) != 1 || wrappers.count(_.event == cyclesRefName) != 1) {
        Future { 0d }
      }

      else {
        val cyclesThread = Future.sequence(wrappers.filter(_.event == cyclesThreadName).head.values)
        val cyclesRef = Future.sequence(wrappers.filter(_.event == cyclesRefName).head.values)

        for {
          cycles <- cyclesThread
          ref <- cyclesRef
        } yield {
          val cyclesVal = math.round(cycles.foldLeft(0l)((acc, value) => acc + value) / (sensorReport.tick.frequency / samplingInterval))
          val refVal = math.round(ref.foldLeft(0l)((acc, value) => acc + value) / (sensorReport.tick.frequency / samplingInterval))

          var coefficient = math.round(cyclesVal / refVal.toDouble).toDouble

          if (coefficient.isNaN || coefficient < formulae.keys.min) coefficient = formulae.keys.min

          if (coefficient > formulae.keys.max) coefficient = formulae.keys.max

          if (!formulae.contains(coefficient)) {
            val coefficientsBefore = formulae.keys.filter(_ < coefficient)
            coefficient = coefficientsBefore.max
          }

          val formula = formulae(coefficient)
          formula(0) = 0
          polyval(formula, cyclesVal)
        }
      }
    }

    val future = Future.sequence(computations)

    future onSuccess {
      case powers: List[Double] => {
        lazy val power = powers.foldLeft(0d)((acc, power) => acc + power)
        publishPowerReport(sensorReport.muid, sensorReport.target, power, PowerUnit.W, "cpu", sensorReport.tick)(eventBus)
      }
    }

    future onFailure {
      case ex: Throwable => {
        log.warning("An error occured: {}", ex.getMessage)
        publishPowerReport(sensorReport.muid, sensorReport.target, 0d, PowerUnit.W, "cpu", sensorReport.tick)(eventBus)
      }
    }
  }
}
