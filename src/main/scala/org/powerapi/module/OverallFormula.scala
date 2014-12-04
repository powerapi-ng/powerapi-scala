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
package org.powerapi.module

import akka.event.LoggingReceive
import org.powerapi.configuration.IdlePowerConfiguration
import org.powerapi.core.{ActorComponent, Configuration, MessageBus}

/**
 * This formula is used for splitting the overall power consumption among processes if
 * the target is Process/Application.
 *
 * The simple CpuSensor is used for getting the Target cpu usage ratio.
 *
 * Otherwise, the overall power consumption is forwarded directly.
 */
class OverallFormula (eventBus: MessageBus) extends ActorComponent with Configuration with IdlePowerConfiguration {
  import org.powerapi.module.OverallPowerChannel.{OverallPower, subscribeOverallPower}
  import org.powerapi.module.cpu.UsageMetricsChannel.{subscribeSimpleUsageReport, UsageReport}
  import org.powerapi.module.PowerChannel.publishPowerReport

  override def preStart(): Unit = {
    subscribeOverallPower(eventBus)(self)
    subscribeSimpleUsageReport(eventBus)(self)
    super.preStart()
  }

  def receive: PartialFunction[Any, Unit] = running(None)

  def running(overallPower: Option[OverallPower]): PartialFunction[Any, Unit] = LoggingReceive {
    case msg: OverallPower => context.become(running(Some(msg)))
    case msg: UsageReport => compute(overallPower, msg)
  } orElse default

  def compute(overallPower: Option[OverallPower], usageReport: UsageReport): Unit = {
    overallPower match {
      case Some(oPower) => {
        publishPowerReport(usageReport.muid, usageReport.target, oPower.power * usageReport.targetRatio.ratio, oPower.unit, oPower.source, usageReport.tick)(eventBus)
      }
      case _ => log.debug("{}", "no OverallPower messages received")
    }
  }
}
