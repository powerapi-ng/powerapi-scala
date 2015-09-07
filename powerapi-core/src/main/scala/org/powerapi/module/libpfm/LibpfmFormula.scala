/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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

import org.powerapi.core.MessageBus
import org.powerapi.core.power._
import org.powerapi.module.FormulaComponent
import org.powerapi.module.libpfm.PerformanceCounterChannel.{PCReport, subscribePCReport}
import org.powerapi.module.PowerChannel.publishRawPowerReport
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
 * This formula is designed to fit a multivariate formula.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmFormula(eventBus: MessageBus, formulae: Map[String, Double], samplingInterval: FiniteDuration)
  extends FormulaComponent[PCReport](eventBus) {

  def subscribeSensorReport(): Unit = {
    subscribePCReport(eventBus)(self)
  }

  def compute(sensorReport: PCReport): Unit = {
    val values: Iterable[Future[(String, Long)]] = for((event, wrappers) <- sensorReport.wrappers.groupBy(_.event)) yield {
      for(values <- Future.sequence((for(wrapper <- wrappers) yield wrapper.values).flatten)) yield event -> math.round(values.sum / (sensorReport.tick.frequency / samplingInterval))
    }

    Future.sequence(values) onComplete {
      case Success(iterator: Iterable[(String, Long)]) => {
        lazy val power = (for(tuple <- iterator) yield formulae.getOrElse(tuple._1, 0d) * tuple._2).sum.W
        publishRawPowerReport(sensorReport.muid, sensorReport.target, power, "cpu", sensorReport.tick)(eventBus)
      }

      case Failure(ex) => {
        log.warning("An error occurred: {}", ex.getMessage)
        publishRawPowerReport(sensorReport.muid, sensorReport.target, 0.W, "cpu", sensorReport.tick)(eventBus)
      }
    }
  }
}
