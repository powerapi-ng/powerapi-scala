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
package org.powerapi.module.cpu.dvfs

import com.typesafe.config.Config
import org.powerapi.core.MessageBus
import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
import org.powerapi.module.PowerChannel
import org.powerapi.module.cpu.UsageMetricsChannel
import scala.collection.JavaConversions

/**
 * CPU formula component giving CPU energy of a given process in computing the ratio between
 * global CPU energy and process CPU usage during a given period.
 *
 * Global CPU energy is given thanks to the well-known global formula: P = c * f * V² [1].
 * This formula operates for an unique frequency/variable but many frequencies can be used by CPU during a time period (e.g using DVFS [2]).
 * Thus, this implementation weights each frequency by the time spent by CPU in working under it.
 *
 * @see [1] "Frequency–Voltage Cooperative CPU Power Control: A Design Rule and Its Application by Feedback Prediction" by Toyama & al.
 * @see [2] http://en.wikipedia.org/wiki/Voltage_and_frequency_scaling.
 *
 * @author <a href="mailto:aurelien.bourdon@gmail.com">Aurélien Bourdon</a
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class CpuFormula(eventBus: MessageBus) extends org.powerapi.module.cpu.simple.CpuFormula(eventBus) {
  import org.powerapi.core.ConfigValue
  import org.powerapi.module.PowerUnit
  import UsageMetricsChannel.subscribeDvfsUsageReport
  import PowerChannel.publishPowerReport

  lazy val constant = (tdp * tdpFactor) / (frequencies.max._1 * math.pow(frequencies.max._2, 2))
  lazy val powers = frequencies.map(frequency => (frequency._1, constant * frequency._1 * math.pow(frequency._2, 2)))

  /**
   * Map of frequencies and their associated voltages.
   */
  lazy val frequencies = load { conf =>
    (for (item <- JavaConversions.asScalaBuffer(conf.getConfigList("powerapi.cpu.frequencies")))
      yield (item.asInstanceOf[Config].getInt("value"), item.asInstanceOf[Config].getDouble("voltage"))).toMap
  } match {
    case ConfigValue(freqs) => freqs
    case _ => Map[Int, Double]()
  }

  override def subscribeSensorReport(): Unit = {
    subscribeDvfsUsageReport(eventBus)(self)
  }

  def power(sensorReport: UsageReport): Option[Double] = {
    val totalPower = powers.foldLeft(0: Double) {
      (acc, power) => acc + (power._2 * sensorReport.timeInStates.times.getOrElse(power._1, 0: Long))
    }
    val time = sensorReport.timeInStates.times.foldLeft(0: Long) {
      (acc, time) => acc + time._2
    }

    if (time == 0) {
      None
    }
    else {
      Some(totalPower / time)
    }
  }

  override def compute(sensorReport: UsageReport): Unit = {
    lazy val p = power(sensorReport) match {
      case Some(p: Double) => p
      case _ => 0d
    }

    publishPowerReport(sensorReport.muid, sensorReport.target, p, PowerUnit.W, "cpu", sensorReport.tick)(eventBus)
  }
}
