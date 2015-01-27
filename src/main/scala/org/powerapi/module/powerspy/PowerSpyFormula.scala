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
package org.powerapi.module.powerspy

import org.powerapi.configuration.IdlePowerConfiguration
import org.powerapi.core.{APIComponent, Configuration, MessageBus}

/**
 * The overall power consumption is distributed among processes if
 * the target is Process/Application.
 *
 * The simple CpuSensor is used for getting the Target cpu usage ratio (UsageReport).
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class PowerSpyFormula(eventBus: MessageBus) extends APIComponent with Configuration with IdlePowerConfiguration {
  import akka.event.LoggingReceive
  import org.powerapi.module.PowerChannel.publishPowerReport
  import org.powerapi.module.cpu.UsageMetricsChannel.{UsageReport, subscribeSimpleUsageReport}
  import org.powerapi.module.powerspy.PowerSpyChannel.{PowerSpyPower, subscribeSensorPower}

  override def preStart(): Unit = {
    subscribeSensorPower(eventBus)(self)
    subscribeSimpleUsageReport(eventBus)(self)
    super.preStart()
  }

  def receive: PartialFunction[Any, Unit] = running(None)

  def running(pspyPower: Option[PowerSpyPower]): PartialFunction[Any, Unit] = LoggingReceive {
    case msg: PowerSpyPower => context.become(running(Some(msg)))
    case msg: UsageReport => compute(pspyPower, msg)
  } orElse default

  def compute(pSpyPower: Option[PowerSpyPower], usageReport: UsageReport): Unit = {
    pSpyPower match {
      case Some(pPower) => {
        publishPowerReport(usageReport.muid, usageReport.target, pPower.power * usageReport.targetRatio.ratio, pPower.unit, pPower.source, usageReport.tick)(eventBus)
      }
      case _ => log.debug("{}", "no PowerSpyPower messages received")
    }
  }
}
