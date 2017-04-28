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

import akka.actor.{Actor, PoisonPill}
import org.powerapi.core.MessageBus
import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick, unsubscribeMonitorTick}
import org.powerapi.core.power.Power
import org.powerapi.core.target.{All, Target}
import org.powerapi.module.Sensor
import org.powerapi.module.hwc.RAPLDomain.RAPLDomain
import org.powerapi.module.hwc.{LikwidHelper, PowerData}
import org.powerapi.module.rapl.RAPLChannel.{Started, State, Stopped, publishRAPLReport}

/**
  * Sensor for collecting power measurements from the RAPL probe (for ALL target).
  *
  * TODO: other targets
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
abstract class RAPLSensor(eventBus: MessageBus, muid: UUID, target: Target, likwidHelper: LikwidHelper, domain: RAPLDomain)
  extends Sensor(eventBus, muid, target) {

  // One core per socket for RAPL (no need to monitor each core)
  private var cores: Option[Seq[Int]] = None

  def init(): Unit = {
    cores = Some(
      likwidHelper.getAffinityDomains().domains
        .filter(_.tag.startsWith("S"))
        .map(_.processorList.head)
    )

    subscribeMonitorTick(muid, target)(eventBus)(self)
  }

  def terminate(): Unit = {
    cores = None
    unsubscribeMonitorTick(muid, target)(eventBus)(self)
  }

  def handler: Actor.Receive = {
    target match {
      case All =>
        val data = startCollect()
        sense(data)
      case _ =>
        unsubscribeMonitorTick(muid, target)(eventBus)(self)
        self ! PoisonPill
        sensorDefault
    }
  }

  def startCollect(): Map[Int, PowerData] = {
    (for (core <- cores.get) yield (core, likwidHelper.powerStart(core, domain))).toMap
  }

  def stopCollect(data: Map[Int, PowerData]): Seq[Double] = {
    (for ((core, pData) <- data) yield {
      val powerData = likwidHelper.powerStop(pData, core)
      likwidHelper.getEnergy(powerData)
    }).toSeq
  }

  def sense(data: Map[Int, PowerData]): Actor.Receive = {
    case msg: MonitorTick =>
      val results = stopCollect(data)
      publishRAPLReport(muid, target, domain, results, msg.tick)(eventBus)
      context.become(sense(startCollect()) orElse sensorDefault)
  }
}
