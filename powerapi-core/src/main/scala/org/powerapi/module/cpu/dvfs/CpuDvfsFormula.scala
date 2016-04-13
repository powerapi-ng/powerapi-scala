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
package org.powerapi.module.cpu.dvfs

import java.util.UUID

import akka.actor.Actor
import akka.event.LoggingReceive

import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.core.{MessageBus, TimeInStates}
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.cpu.UsageMetricsChannel.{DvfsUsageReport, subscribeDvfsUsageReport, unsubscribeDvfsUsageReport}

/**
  * CPU formula component giving CPU energy of a given target by computing the ratio between
  * global CPU energy and target CPU usage during a given period.
  *
  * Global CPU energy is given thanks to the well-known global formula: P = c * f * V² [1].
  * This formula operates for an unique frequency/variable but many frequencies can be used by CPU during a time period (e.g using DVFS [2]).
  * Thus, this implementation weights each frequency by the time spent by CPU in working under it.
  *
  * @see [1] "Frequency–Voltage Cooperative CPU Power Control: A Design Rule and Its Application by Feedback Prediction" by Toyama & al.
  * @see [2] http://en.wikipedia.org/wiki/Voltage_and_frequency_scaling.
  * @author <a href="mailto:aurelien.bourdon@gmail.com">Aurélien Bourdon</a
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class CpuDvfsFormula(eventBus: MessageBus, muid: UUID, target: Target, tdp: Double, tdpFactor: Double, frequencies: Map[Int, Double])
  extends Formula(eventBus, muid, target) {

  val constant = (tdp * tdpFactor) / (frequencies.max._1 * math.pow(frequencies.max._2, 2))
  val powers = frequencies.map(frequency => (frequency._1, constant * frequency._1 * math.pow(frequency._2, 2)))

  def init(): Unit = subscribeDvfsUsageReport(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeDvfsUsageReport(muid, target)(eventBus)(self)

  def handler: Actor.Receive = LoggingReceive {
    case msg: DvfsUsageReport =>
      publishRawPowerReport(msg.muid, msg.target, power(msg.timeInStates), "cpu", msg.tick)(eventBus)
  }

  def power(timeInStates: TimeInStates): Power = {
    val totalPower = powers.map {
      case (frequency, p) => p * timeInStates.times.getOrElse(frequency, 0l)
    }.sum

    val totalTime = timeInStates.times.values.sum

    if (totalTime != 0) (totalPower / totalTime.toDouble).W
    else 0.W
  }
}
