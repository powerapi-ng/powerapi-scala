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
package org.powerapi.module

import java.util.UUID

import akka.actor.ActorRef

import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.core.{Channel, Message, MessageBus, Tick}

/**
  * PowerChannel channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
  * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
  */
object PowerChannel extends Channel {

  type M = PowerReport

  /**
    * Publish a RawPowerReport in the right topic.
    */
  def publishRawPowerReport(muid: UUID, target: Target, power: Power, device: String, tick: Tick): MessageBus => Unit = {
    publish(RawPowerReport(rawPowerReportMuid(muid), muid, target, power, device, tick))
  }

  /**
    * Publish an AggregatedPowerReport in the right topic.
    */
  def render(aggR: AggregatePowerReport): MessageBus => Unit = {
    publish(aggR)
  }

  /**
    * Used to subscribe/unsubscribe to RawPowerReport on the right topic.
    */
  def subscribeRawPowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    subscribe(rawPowerReportMuid(muid))
  }

  /**
    * Use to format a MUID to an associated topic.
    */
  private def rawPowerReportMuid(muid: UUID): String = {
    s"power:$muid"
  }

  def unsubscribeRawPowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    unsubscribe(rawPowerReportMuid(muid))
  }

  /**
    * Used to subscribe/unsubscribe to AggPowerReport on the right topic.
    */
  def subscribeAggPowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    subscribe(aggPowerReportTopic(muid))
  }

  def unsubscribeAggPowerReport(muid: UUID): MessageBus => ActorRef => Unit = {
    unsubscribe(aggPowerReportTopic(muid))
  }

  private def aggPowerReportTopic(muid: UUID): String = {
    s"reporter:$muid"
  }

  /**
    * Base trait for each power report
    */
  trait PowerReport extends Message {
    def muid: UUID

    def tick: Tick
  }

  /**
    * RawPowerReport is represented as a dedicated type of message.
    *
    * @param topic  subject used for routing the message.
    * @param muid   monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target monitor target.
    * @param power  target's power consumption.
    * @param device device targeted.
    * @param tick   tick origin.
    */
  case class RawPowerReport(topic: String,
                            muid: UUID,
                            target: Target,
                            power: Power,
                            device: String,
                            tick: Tick) extends PowerReport

  /**
    * AggregatePowerReport is represented as a dedicated type of message.
    */
  case class AggregatePowerReport(muid: UUID) extends PowerReport {
    val topic: String = aggPowerReportTopic(muid)
    private val reports = collection.mutable.Buffer[RawPowerReport]()
    private var _aggregator: Option[Seq[Power] => Power] = None

    def +=(powerReport: RawPowerReport): Unit = {
      reports += powerReport
    }

    def size: Int = reports.size

    def targets: Set[Target] = reports.map(_.target).toSet

    def devices: Set[String] = reports.map(_.device).toSet

    def power = aggregator.getOrElse(SUM _)(reports.map(_.power))

    def aggregator: Option[Seq[Power] => Power] = _aggregator

    def aggregator_=(agg: Option[Seq[Power] => Power]): Unit = {
      _aggregator = agg
    }

    def tick: Tick = {
      if (reports.map(_.tick).toSet.size == 1) reports.head.tick
      else new Tick {
        val topic = ""
        val timestamp = System.currentTimeMillis()
      }
    }
  }

}
