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

import java.io.File
import java.util.UUID

import akka.actor.{Actor, PoisonPill}
import org.powerapi.core.{MessageBus, OSHelper}
import org.powerapi.core.target.{All, Application, Container, Process, Target}
import org.powerapi.module.Sensor
import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick, unsubscribeMonitorTick}
import org.powerapi.module.hwc.HWCChannel._

/**
  * Collects hardware counters at the core level (for All, and Container target).
  * TODO: APP target
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class HWCCoreSensor(eventBus: MessageBus, muid: UUID, target: Target,
                    osHelper: OSHelper, likwidHelper: LikwidHelper, cHelper: CHelper, events: Seq[String])
  extends Sensor(eventBus, muid, target) {

  private var hwThreads: Option[Seq[HWThread]] = None
  private var groupId: Option[Int] = None

  def init(): Unit = {
    hwThreads = Some(likwidHelper.getCpuTopology().threadPool)

    target match {
      case Container(id, _) =>
        osHelper.cgroupMntPoint("perf_event") match {
          case Some(path) =>
            new File(s"$path/docker").listFiles.filter(f => f.isDirectory && f.getName.startsWith(id)).map(_.getName).headOption match {
              case Some(fullId) =>
                val fd = java.lang.Long.toHexString(cHelper.open(s"$path/docker/$fullId", 0))
                val flags = java.lang.Long.toHexString(1L << 2)
                val eventsExtended = events.map(event => s"$event:PERF_PID=$fd:PERF_FLAGS=$flags").mkString(",")

                groupId = Some(likwidHelper.perfmonAddEventSet(eventsExtended))
                if (groupId.get < 0) {
                  log.error("Failed to add events string {} to LIKWID's performance monitoring module", eventsExtended)
                }

                if (likwidHelper.perfmonSetupCounters(groupId.get) < 0) {
                  log.error("Failed to setup group {} in LIKWID's performance monitoring module", s"$groupId")
                }

              case _ =>
                self ! PoisonPill
            }
          case _ =>
            self ! PoisonPill
        }
      case All =>
        val pid = java.lang.Long.toHexString(-1)
        val flags = java.lang.Long.toHexString(0)
        val eventsExtended = events.map(event => s"$event:PERF_PID=$pid:PERF_FLAGS=$flags").mkString(",")

        groupId = Some(likwidHelper.perfmonAddEventSet(eventsExtended))
        if (groupId.get < 0) {
          log.error("Failed to add events string {} to LIKWID's performance monitoring module", eventsExtended)
        }

        if (likwidHelper.perfmonSetupCounters(groupId.get) < 0) {
          log.error("Failed to setup group {} in LIKWID's performance monitoring module", s"$groupId")
        }
    }

    subscribeMonitorTick(muid, target)(eventBus)(self)
  }

  def terminate(): Unit = {
    hwThreads = None
    groupId = None
    unsubscribeMonitorTick(muid, target)(eventBus)(self)
  }

  def handler: Actor.Receive = {
    target match {
      case t if t.isInstanceOf[Application] || t.isInstanceOf[Process] =>
        unsubscribeMonitorTick(muid, target)(eventBus)(self)
        self ! PoisonPill
        sensorDefault
      case _ =>
        startCollect()
        sense
    }
  }

  def startCollect(): Unit = {
    groupId match {
      case Some(id) =>
        likwidHelper.perfmonStartGroupCounters(groupId.get)
      case _ =>

    }
  }

  def stopCollect(): Seq[HWC] = {
    groupId match {
      case Some(id) =>
        likwidHelper.perfmonStopGroupCounters(id)

        for {
          hwThread <- hwThreads.get
          (event, index) <- events.zipWithIndex
        } yield {
          HWC(hwThread, event, likwidHelper.perfmonGetLastResult(groupId.get, index, hwThread.apicId))
        }
      case _ =>
        Seq()
    }
  }

  def sense: Actor.Receive = {
    case msg: MonitorTick =>
      val results = stopCollect()
      publishHWCReport(muid, target, results, msg.tick)(eventBus)
      startCollect()
  }
}
