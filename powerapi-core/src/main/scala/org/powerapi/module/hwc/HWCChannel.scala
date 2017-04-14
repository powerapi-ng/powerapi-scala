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

import akka.actor.ActorRef
import org.powerapi.core.target.Target
import org.powerapi.core.{Channel, Message, MessageBus, Tick}

/**
  * HardwareCounterChannel channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object HWCChannel extends Channel {

  type M = HWCReport

  def publishHWCReport(muid: UUID, target: Target, values: Seq[HWC], tick: Tick): MessageBus => Unit = {
    publish(HWCReport(hwcReportToTopic(muid, target), muid, target, values, tick))
  }

  def hwcReportToTopic(muid: UUID, target: Target): String = {
    s"hwc-core-sensor:$muid-$target"
  }

  def subscribeHWCReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(hwcReportToTopic(muid, target))
  }

  def unsubscribeHWCReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(hwcReportToTopic(muid, target))
  }

  trait State
  object Started extends State
  object Stopped extends State

  case class HWC(hwThread: HWThread, event: String, value: Double)

  /**
    * HWCReport is represented as a dedicated type of message.
    *
    * @param topic    : subject used for routing the message.
    * @param muid     : monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target   : monitor target.
    * @param values   : counters values.
    * @param tick     : tick origin.
    */
  case class HWCReport(topic: String,
                       muid: UUID,
                       target: Target,
                       values: Seq[HWC],
                       tick: Tick) extends Message
}
