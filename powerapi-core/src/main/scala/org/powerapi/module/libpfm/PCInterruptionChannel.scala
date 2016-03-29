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
package org.powerapi.module.libpfm

import java.util.UUID

import akka.actor.ActorRef
import org.powerapi.core.target.Target
import org.powerapi.core.{Channel, Message, MessageBus, Tick}
import org.powerapi.module.libpfm.PayloadProtocol.Payload

case class AgentTick(topic: String, timestamp: Long, payload: Payload) extends Tick

/**
  * InterruptionChannel channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object PCInterruptionChannel extends Channel {

  type M = InterruptionPCReport

  /**
    * Publish an InterruptionPCReport in the event bus.
    */
  def publishInterruptionPCReport(muid: UUID, target: Target, wrappers: Seq[InterruptionPCWrapper], tick: Tick): MessageBus => Unit = {
    publish(InterruptionPCReport(interruptionPCReportToTopic(muid, target), muid, target, wrappers, tick))
  }

  /**
    * Used to subscribe to InterruptionPCReport on the right topic.
    */
  def subscribeInterruptionPCReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(interruptionPCReportToTopic(muid, target))
  }

  /**
    * Used to unsubscribe to InterruptionPCReport on the right topic.
    */
  def unsubscribeInterruptionPCReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(interruptionPCReportToTopic(muid, target))
  }

  /**
    * Used to format the topic used to interact with the FormulaChild actors.
    */
  def interruptionPCReportToTopic(muid: UUID, target: Target): String = {
    s"libpfm-interruption-sensor:$muid-$target"
  }

  /**
    * Extended tick to keep more information when an interruption occurs.
    *
    * @param cpu            cpu id of the running core when the interruption was launched.
    * @param tid            thread id at the origin of the interruption.
    * @param fullMethodName method name which is at the origin of the interruption.
    * @param timestamp      origin timestamp of the interruption (in nanoseconds).
    * @param triggering     is it the tick at the origin of the interruption?
    */
  case class InterruptionTick(topic: String,
                              cpu: Int,
                              tid: TID,
                              fullMethodName: String,
                              timestamp: Long,
                              triggering: Boolean) extends Tick

  /**
    * Internal message.
    * One message per core/event.
    */
  case class InterruptionPCWrapper(core: Int, event: String, values: Seq[InterruptionHWCounter])

  /**
    * Internal message used to wrap an hardware counter value received after an interruption.
    */
  case class InterruptionHWCounter(cpu: Int, tid: Int, fullMethodName: String, value: Long, triggering: Boolean)

  /**
    * InterruptionPCReport is represented as a dedicated type of message.
    *
    * @param topic    subject used for routing the message.
    * @param muid     monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target   monitor target.
    * @param wrappers performance counter wrappers.
    * @param tick     tick origin.
    */
  case class InterruptionPCReport(topic: String,
                                  muid: UUID,
                                  target: Target,
                                  wrappers: Seq[InterruptionPCWrapper],
                                  tick: Tick) extends Message
}
