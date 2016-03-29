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

import collection.JavaConversions._

import akka.actor.{PoisonPill, Actor}

import org.powerapi.core.{Tick, MessageBus}
import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick, unsubscribeMonitorTick}
import org.powerapi.core.target.{Process, Target}
import org.powerapi.module.Sensor
import org.powerapi.module.libpfm.PCInterruptionChannel.{InterruptionHWCounter, InterruptionPCWrapper, publishInterruptionPCReport}
import org.powerapi.module.libpfm.PayloadProtocol.Payload

/**
  * Libpfm sensor component which used information sent by a PowerAPI agent.
  * The PowerAPI agent uses the interruption mode to collect performance counters and stack traces.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class LibpfmInterruptionCoreSensor(eventBus: MessageBus, muid: UUID, target: Target, topology: Map[Int, Set[Int]], events: Set[String])
  extends Sensor(eventBus, muid, target) {

  val combinations = {
    for {
      core: Int <- topology.keys
      index: Int <- topology(core)
      event: String <- events
    } yield (core, index, event)
  }.toParArray

  def init(): Unit = subscribeMonitorTick(muid, target)(eventBus)(self)

  def terminate(): Unit = unsubscribeMonitorTick(muid, target)(eventBus)(self)

  def handler: Actor.Receive = {
    if (target.isInstanceOf[Process]) {
      sense(Map())
    }

    else {
      unsubscribeMonitorTick(muid, target)(eventBus)(self)
      self ! PoisonPill
      sensorDefault
    }
  }

  // payloads: Map[cpu index -> Payload]
  def sense(payloads: Map[Int, Payload]): Actor.Receive = {
    case msg: MonitorTick if msg.tick.isInstanceOf[AgentTick] =>
      val tick = msg.tick.asInstanceOf[AgentTick]

      if (tick.payload.getTracesCount == 0) {
        val currentPayloads = payloads.find(_._2.getTid == tick.payload.getTid) match {
          case Some((core, _)) => payloads - core
          case _ => payloads
        }

        context.become(sense(currentPayloads) orElse sensorDefault)
      }

      else {
        val currentPayloads = (payloads.find(_._2.getTid == tick.payload.getTid) match {
          case Some((core, _)) => payloads - core
          case _ => payloads
        }) + (tick.payload.getCore -> tick.payload)

        val allWrappers = for ((core, index, event) <- combinations if currentPayloads.contains(index)) yield {
          val payload = currentPayloads(index)
          val triggering = tick.payload.getTimestamp == payload.getTimestamp
          val counter = InterruptionHWCounter(index, payload.getTid, payload.getTracesList.map(_.getValue).reverse.mkString("."), payload.getCountersList.filter(_.getKey == event).head.getValue, triggering)
          InterruptionPCWrapper(core, event, List(counter))
        }

        publishInterruptionPCReport(muid, target, allWrappers.groupBy(wrapper => (wrapper.core, wrapper.event)).map {
          case ((core, event), wrappers) => InterruptionPCWrapper(core, event, wrappers.flatMap(_.values).toList)
        }.toList, new Tick { val topic = ""; val timestamp = tick.payload.getTimestamp })(eventBus)

        context.become(sense(currentPayloads) orElse sensorDefault)
      }
  }
}
