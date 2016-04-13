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
package org.powerapi.module.cpu

import java.util.UUID

import akka.actor.ActorRef

import org.powerapi.core.target.{Target, TargetUsageRatio}
import org.powerapi.core.{Channel, Message, MessageBus, Tick, TimeInStates}

/**
  * UsageMetricsChannel channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object UsageMetricsChannel extends Channel {

  type M = UsageReport

  /**
    * Publish a SimpleUsageReport in the event bus.
    */
  def publishUsageReport(muid: UUID, target: Target, targetRatio: TargetUsageRatio, tick: Tick): MessageBus => Unit = {
    publish(SimpleUsageReport(simpleUsageReportTopic(muid, target), muid, target, targetRatio, tick))
  }

  /**
    * Used to format the topic used to interact with the FormulaChild actors.
    */
  def simpleUsageReportTopic(muid: UUID, target: Target): String = {
    s"cpu-simple-sensor:$muid-$target"
  }

  /**
    * Publish a DvfsUsageReport in the event bus.
    */
  def publishUsageReport(muid: UUID, target: Target, targetRatio: TargetUsageRatio, timeInStates: TimeInStates, tick: Tick): MessageBus => Unit = {
    publish(DvfsUsageReport(dvfsUsageReportTopic(muid, target), muid, target, targetRatio, timeInStates, tick))
  }

  /**
    * Used to subscribe to SimpleUsageReport on the right topic.
    */
  def subscribeSimpleUsageReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(simpleUsageReportTopic(muid, target))
  }

  /**
    * Used to subscribe to DvfsUsageReport on the right topic.
    */
  def subscribeDvfsUsageReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(dvfsUsageReportTopic(muid, target))
  }

  /**
    * Used to unsubscribe to SimpleUsageReport on the right topic.
    */
  def unsubscribeSimpleUsageReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(simpleUsageReportTopic(muid, target))
  }

  /**
    * Used to unsubscribe to DvfsUsageReport on the right topic.
    */
  def unsubscribeDvfsUsageReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(dvfsUsageReportTopic(muid, target))
  }

  /**
    * Used to format the topic used to interact with the FormulaChild actors.
    */
  def dvfsUsageReportTopic(muid: UUID, target: Target): String = {
    s"cpu-dvfs-sensor:$muid-$target"
  }

  trait UsageReport extends Message

  /**
    * SimpleUsageReport is represented as a dedicated type of message.
    *
    * @param topic       subject used for routing the message.
    * @param muid        monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target      monitor target.
    * @param targetRatio target cpu ratio usage.
    * @param tick        tick origin.
    */
  case class SimpleUsageReport(topic: String,
                               muid: UUID,
                               target: Target,
                               targetRatio: TargetUsageRatio,
                               tick: Tick) extends UsageReport

  /**
    * DvfsUsageReport is represented as a dedicated type of message.
    *
    * @param topic        subject used for routing the message.
    * @param muid         monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target       monitor target.
    * @param targetRatio  target cpu ratio usage.
    * @param timeInStates time spent by the CPU in its frequencies.
    * @param tick         tick origin.
    */
  case class DvfsUsageReport(topic: String,
                             muid: UUID,
                             target: Target,
                             targetRatio: TargetUsageRatio,
                             timeInStates: TimeInStates,
                             tick: Tick) extends UsageReport

}
