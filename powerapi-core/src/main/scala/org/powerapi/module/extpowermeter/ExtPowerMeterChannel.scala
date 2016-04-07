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
import org.powerapi.core.{Channel, Message, MessageBus, Tick}

/**
  * PowerMeterChannel channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
  */
object ExtPowerMeterChannel extends Channel {

  type M = ExtPowerMeterReport
  /**
    * Topic for communicating with the SensorChild actors.
    */
  private val pspyTopic = "powerspy:power"
  private val g5kOmegaWTopic = "g5k-omegawatt:power"
  private val raplTopic = "rapl:power"

  /**
    * Used to subscribe/unsubscribe to ExtPowerMeterRawPowerReport on the right topic.
    */
  def subscribePowerSpyRawPowerReport: MessageBus => ActorRef => Unit = {
    subscribe(pspyTopic)
  }

  def unsubscribePowerSpyRawPowerReport: MessageBus => ActorRef => Unit = {
    unsubscribe(pspyTopic)
  }

  def subscribeG5KOmegaWattRawPowerReport: MessageBus => ActorRef => Unit = {
    subscribe(g5kOmegaWTopic)
  }

  def unsubscribeG5KOmegaWattRawPowerReport: MessageBus => ActorRef => Unit = {
    unsubscribe(g5kOmegaWTopic)
  }

  def subscribeRAPLRawPowerReport: MessageBus => ActorRef => Unit = {
    subscribe(raplTopic)
  }

  def unsubscribeRAPLRawPowerReport: MessageBus => ActorRef => Unit = {
    unsubscribe(raplTopic)
  }

  /**
    * Used to subscribe/unsubscribe to ExtPowerMeterPowerReport on the right topic.
    */
  def subscribePowerSpyPowerReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(powerSpyPowerReportTopic(muid, target))
  }

  def unsubscribePowerSpyPowerReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(powerSpyPowerReportTopic(muid, target))
  }

  /**
    * Used to format the topic used to interact with the FormulaChild actors.
    */
  def powerSpyPowerReportTopic(muid: UUID, target: Target): String = {
    s"powerspy-sensor:$muid-$target"
  }

  def subscribeG5KOmegaWattPowerReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(g5kOmegawattPowerReportTopic(muid, target))
  }

  def g5kOmegawattPowerReportTopic(muid: UUID, target: Target): String = {
    s"g5k-omegawatt-sensor:$muid-$target"
  }

  def unsubscribeG5KOmegaWattPowerReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(g5kOmegawattPowerReportTopic(muid, target))
  }

  def subscribeRAPLPowerReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(raplPowerReportTopic(muid, target))
  }

  def unsubscribeRAPLPowerReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(raplPowerReportTopic(muid, target))
  }

  def raplPowerReportTopic(muid: UUID, target: Target): String = {
    s"rapl-sensor:$muid-$target"
  }

  /**
    * Publish a ExtPowerMeterRawPowerReport in the event bus.
    */
  def publishPowerSpyRawPowerReport(power: Power): MessageBus => Unit = {
    publish(ExtPowerMeterRawPowerReport(pspyTopic, power, "powerspy"))
  }

  def publishG5KOmegaWattRawPowerReport(power: Power): MessageBus => Unit = {
    publish(ExtPowerMeterRawPowerReport(g5kOmegaWTopic, power, "g5k-omegawatt"))
  }

  def publishRAPLRawPowerReport(power: Power): MessageBus => Unit = {
    publish(ExtPowerMeterRawPowerReport(raplTopic, power, "rapl"))
  }

  /**
    * Publish a ExtPowerMeterReport in the event bus.
    */
  def publishPowerSpyPowerReport(muid: UUID, target: Target, targetRatio: TargetUsageRatio, power: Power, tick: Tick): MessageBus => Unit = {
    publish(ExtPowerMeterPowerReport(powerSpyPowerReportTopic(muid, target), muid, target, targetRatio, power, "powerspy", tick))
  }

  def publishG5KOmegaWattPowerReport(muid: UUID, target: Target, targetRatio: TargetUsageRatio, power: Power, tick: Tick): MessageBus => Unit = {
    publish(ExtPowerMeterPowerReport(g5kOmegawattPowerReportTopic(muid, target), muid, target, targetRatio, power, "g5k-omegawatt", tick))
  }

  def publishRAPLPowerReport(muid: UUID, target: Target, targetRatio: TargetUsageRatio, power: Power, tick: Tick): MessageBus => Unit = {
    publish(ExtPowerMeterPowerReport(raplPowerReportTopic(muid, target), muid, target, targetRatio, power, "rapl", tick))
  }

  trait ExtPowerMeterReport extends Message

  /**
    * ExtPowerMeterRawPowerReport is represented as a dedicated type of message.
    *
    * @param topic  subject used for routing the message.
    * @param power  power consumption got by an external device.
    * @param source power's source.
    */
  case class ExtPowerMeterRawPowerReport(topic: String,
                                         power: Power,
                                         source: String) extends ExtPowerMeterReport

  /**
    * ExtPowerMeterPowerReport is represented as a dedicated type of message.
    *
    * @param topic       subject used for routing the message.
    * @param muid        monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target      monitor target.
    * @param targetRatio target cpu ratio usage.
    * @param power       power consumption got by an external device.
    * @param source      power's source.
    */
  case class ExtPowerMeterPowerReport(topic: String,
                                      muid: UUID,
                                      target: Target,
                                      targetRatio: TargetUsageRatio,
                                      power: Power,
                                      source: String,
                                      tick: Tick) extends ExtPowerMeterReport

}
