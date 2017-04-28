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
package org.powerapi.module.rapl

import java.util.UUID

import akka.actor.ActorRef
import org.powerapi.core.target.Target
import org.powerapi.core.{Channel, Message, MessageBus, Tick}
import org.powerapi.module.hwc.RAPLDomain.RAPLDomain

/**
  * HardwareCounterChannel channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object RAPLChannel extends Channel {

  type M = RAPLReport

  def publishRAPLReport(muid: UUID, target: Target, domain: RAPLDomain, energies: Seq[Double], tick: Tick): MessageBus => Unit = {
    publish(RAPLReport(raplReportToTopic(muid, target, domain), muid, target, energies, tick))
  }

  def raplReportToTopic(muid: UUID, target: Target, domain: RAPLDomain): String = {
    s"rapl-${domain.id}-sensor:$muid-$target"
  }

  def subscribeRAPLReport(muid: UUID, target: Target, domain: RAPLDomain): MessageBus => ActorRef => Unit = {
    subscribe(raplReportToTopic(muid, target, domain))
  }

  def unsubscribeRAPLReport(muid: UUID, target: Target, domain: RAPLDomain): MessageBus => ActorRef => Unit = {
    unsubscribe(raplReportToTopic(muid, target, domain))
  }

  trait State
  object Started extends State
  object Stopped extends State

  /**
    * RAPLReport is represented as a dedicated type of message.
    *
    * @param topic    : subject used for routing the message.
    * @param muid     : monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target   : monitor target.
    * @param energies : energy values (domain level).
    * @param tick     : tick origin.
    */
  case class RAPLReport(topic: String,
                        muid: UUID,
                        target: Target,
                        energies: Seq[Double],
                        tick: Tick) extends Message
}
