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
package org.powerapi.module.disk.simple

import java.time.Clock
import java.util.UUID

import akka.actor.Actor
import akka.event.LoggingReceive
import org.joda.time.DateTime
import org.powerapi.core.MessageBus
import org.powerapi.core.target.Target
import org.powerapi.core.power._
import org.powerapi.module.Formula
import org.powerapi.module.PowerChannel.publishRawPowerReport
import org.powerapi.module.disk.UsageMetricsChannel.{DiskUsageReport, subscribeDiskUsageReport, unsubscribeDiskUsageReport}

import scala.concurrent.duration.FiniteDuration

/**
  * Implements a DiskFormula by spreading the global ssd power consumption with the target's disk usage.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class DiskSimpleFormula(eventBus: MessageBus, muid: UUID, target: Target, interval: FiniteDuration, formulae: Map[String, Map[String, Seq[PieceWiseFunction]]]) extends Formula(eventBus, muid, target) {

  def init(): Unit = subscribeDiskUsageReport(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeDiskUsageReport(muid, target)(eventBus)(self)

  def handler: Actor.Receive = compute(System.nanoTime())

  def compute(old: Long): Actor.Receive = {
    case msg: DiskUsageReport =>
      val now = new DateTime().getMillis

      val powers = for (usage <- msg.usages) yield {

        val diskFormula = formulae.getOrElse(usage.name, Map("read" -> Seq(), "write" -> Seq()))
        val readFunctions = diskFormula("read")
        val writeFunctions = diskFormula("write")

        val readPower = readFunctions.collectFirst {
          case function if function.condition.test(usage.bytesRead) =>
            val readCoeffs = function.coeffs
            val bytesRead = usage.bytesRead * (interval.toMillis / (now - old).toDouble)
            usage.readRatio * (readCoeffs(0) + readCoeffs(1) * bytesRead)
        }

        val writePower = writeFunctions.collectFirst {
          case function if function.condition.test(usage.bytesWritten) =>
            val writeCoeffs = function.coeffs
            val bytesWritten = usage.bytesWritten * (interval.toMillis / (now - old).toDouble)
            usage.writeRatio * (writeCoeffs(0) + writeCoeffs(1) * bytesWritten)
        }

        readPower.getOrElse(0.0) + writePower.getOrElse(0.0)
      }

      publishRawPowerReport(muid, target, powers.sum.W, "disk", msg.tick)(eventBus)
      context.become(compute(now) orElse formulaDefault)
  }
}