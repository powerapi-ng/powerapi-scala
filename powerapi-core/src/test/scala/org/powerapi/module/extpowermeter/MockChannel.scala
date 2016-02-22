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
package org.powerapi.module.extpowermeter

import java.util.UUID

import akka.actor.ActorRef

import org.powerapi.core.power.Power
import org.powerapi.core.target.{Target, TargetUsageRatio}
import org.powerapi.core.{Channel, MessageBus, Tick}
import org.powerapi.module.extpowermeter.ExtPowerMeterChannel.{ExtPowerMeterPowerReport, ExtPowerMeterRawPowerReport, ExtPowerMeterReport}

object MockChannel extends Channel {
  type M = ExtPowerMeterReport

  def subscribeRawReportMock: MessageBus => ActorRef => Unit = {
    subscribe("extmock:power")
  }

  def unsubscribeRawReportMock: MessageBus => ActorRef => Unit = {
    unsubscribe("extmock:power")
  }

  def publishRawReportMock(power: Power): MessageBus => Unit = {
    publish(ExtPowerMeterRawPowerReport("extmock:power", power, "extmock"))
  }

  def publishReportMock(muid: UUID, target: Target, targetRatio: TargetUsageRatio, power: Power, tick: Tick): MessageBus => Unit = {
    publish(ExtPowerMeterPowerReport(powerReportMockTopic(muid, target), muid, target, targetRatio, power, "extmock", tick))
  }

  def subscribeReportMock(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(powerReportMockTopic(muid, target))
  }

  def powerReportMockTopic(muid: UUID, target: Target): String = {
    s"extmock-sensor:$muid-$target"
  }

  def unsubscribeReportMock(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(powerReportMockTopic(muid, target))
  }
}
