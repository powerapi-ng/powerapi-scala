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
package org.powerapi.core

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorRef

import org.powerapi.core.power.Power
import org.powerapi.core.target.Target

/**
  * Monitor channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
  */
object MonitorChannel extends Channel {

  type M = MonitorMessage
  /**
    * Topic for communicating with the Monitors actor.
    */
  private val topic = "monitor:handling"

  /**
    * Used to subscribe/unsubscribe to MonitorTick on the right topic.
    */
  def subscribeMonitorTick(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(monitorTickTopic(muid, target))
  }

  def unsubscribeMonitorTick(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(monitorTickTopic(muid, target))
  }

  /**
    * Used to format the topic used to interact with the SensorChild actors.
    */
  def monitorTickTopic(muid: UUID, target: Target): String = {
    s"monitor:$muid-$target"
  }

  /**
    * Used to interact with the Supervisor.
    */
  def startMonitor(muid: UUID, targets: Set[Target]): MessageBus => Unit = {
    publish(MonitorStart(topic, muid, targets))
  }

  def stopMonitor(muid: UUID): MessageBus => Unit = {
    publish(MonitorStop(topic, muid))
  }

  def stopAllMonitor: MessageBus => Unit = {
    publish(MonitorStopAll(topic))
  }

  def setAggregator(muid: UUID, aggFunction: Seq[Power] => Power): MessageBus => Unit = {
    publish(MonitorAggregator(topic, muid, aggFunction))
  }

  def setFrequency(muid: UUID, frequency: FiniteDuration): MessageBus => Unit = {
    publish(MonitorFrequency(topic, muid, frequency))
  }

  /**
    * Used to subscribe to MonitorMessage on the right topic.
    */
  def subscribeMonitorsChannel: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }

  /**
    * Used to publish MonitorTick on the right topic.
    */
  def publishMonitorTick(muid: UUID, target: Target, tick: Tick): MessageBus => Unit = {
    publish(MonitorTick(monitorTickTopic(muid, target), muid, target, tick))
  }

  /**
    * Used to format the MonitorChild name.
    */
  def formatMonitorChildName(muid: UUID): String = {
    s"monitor-$muid"
  }

  trait MonitorMessage extends Message

  /**
    * MonitorTick is represented as a dedicated type of message.
    *
    * @param topic  subject used for routing the message.
    * @param muid   monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target monitor target.
    * @param tick   tick origin.
    */
  case class MonitorTick(topic: String,
                         muid: UUID,
                         target: Target,
                         tick: Tick) extends MonitorMessage

  /**
    * MonitorAggregator is represented as a dedicated type of message.
    *
    * @param topic      subject used for routing the message.
    * @param muid       monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param aggregator aggregate power estimation for a specific sample of power reports.
    */
  case class MonitorAggregator(topic: String,
                               muid: UUID,
                               aggregator: Seq[Power] => Power) extends MonitorMessage

  /**
    * MonitorStart is represented as a dedicated type of message.
    *
    * @param topic   subject used for routing the message.
    * @param muid    monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param targets monitor targets.
    */
  case class MonitorStart(topic: String,
                          muid: UUID,
                          targets: Set[Target]) extends MonitorMessage

  /**
    * MonitorFrequency is represented as a dedicated type of message.
    *
    * @param topic     subject used for routing the message.
    * @param muid      monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param frequency clock frequency.
    */
  case class MonitorFrequency(topic: String,
                              muid: UUID,
                              frequency: FiniteDuration) extends MonitorMessage

  /**
    * MonitorStop is represented as a dedicated type of message.
    *
    * @param topic subject used for routing the message.
    * @param muid  monitor unique identifier (MUID), which is at the origin of the report flow.
    */
  case class MonitorStop(topic: String, muid: UUID) extends MonitorMessage

  /**
    * MonitorStopAll is represented as a dedicated type of message.
    *
    * @param topic subject used for routing the message.
    */
  case class MonitorStopAll(topic: String) extends MonitorMessage

}
