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

import akka.actor.{Actor, ActorRef}

import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick, unsubscribeMonitorTick}
import org.powerapi.core.power.{Power, _}
import org.powerapi.core.target.{All, Target, TargetUsageRatio}
import org.powerapi.core.{ExternalPMeter, MessageBus, OSHelper, Tick}
import org.powerapi.module.Sensor
import org.powerapi.module.extpowermeter.ExtPowerMeterChannel.ExtPowerMeterRawPowerReport

/**
  * Base implementation for external power meter sensors.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
abstract class ExtPowerMeterSensor(eventBus: MessageBus, muid: UUID, target: Target,
                                   subscribeExtRawPowerReport: MessageBus => ActorRef => Unit, unsubscribeExtRawPowerReport: MessageBus => ActorRef => Unit,
                                   publishExtPowerReport: (UUID, Target, TargetUsageRatio, Power, Tick) => MessageBus => Unit,
                                   osHelper: OSHelper, pMeter: ExternalPMeter, idlePower: Power) extends Sensor(eventBus, muid, target) {

  def init(): Unit = {
    subscribeExtRawPowerReport(eventBus)(self)
    subscribeMonitorTick(muid, target)(eventBus)(self)
    pMeter.init(eventBus)
    pMeter.start()
  }

  def terminate(): Unit = {
    unsubscribeExtRawPowerReport(eventBus)(self)
    unsubscribeMonitorTick(muid, target)(eventBus)(self)
    pMeter.stop()
  }

  def currentTimes(target: Target): (Long, Long) = {
    val globalTimes = osHelper.getGlobalCpuTimes
    val targetTime = osHelper.getTargetCpuTime(target)
    (targetTime, globalTimes.activeTime)
  }

  def usageRatio(oldT: Long, newT: Long, oldG: Long, newG: Long): TargetUsageRatio = {
    val targetTime = if (newT - oldT > 0) newT - oldT else 0
    val globalTime = if (newG - oldG > 0) newG - oldG else 0

    if (globalTime > 0) {
      TargetUsageRatio(targetTime / globalTime.toDouble)
    }
    else
      TargetUsageRatio(0)
  }

  def power(report: Option[ExtPowerMeterRawPowerReport]): Power = {
    report match {
      case Some(r) =>
        val activePower = r.power.toMilliWatts - idlePower.toMilliWatts
        try {
          if (activePower > 0) activePower.mW else 0.mW
        }
        catch {
          case _: Exception =>
            log.warning("The power value is out of range. Skip.")
            0.W
        }
      case _ =>
        0.mW
    }
  }

  def handler: Actor.Receive = {
    if (target == All) {
      sense(None, 0l, 0l)
    }
    else {
      val initTimes = currentTimes(target)
      sense(None, initTimes._1, initTimes._2)
    }
  }

  def sense(report: Option[ExtPowerMeterRawPowerReport], oldTargetTime: Long, oldGlobalTime: Long): Actor.Receive = {
    case msg: ExtPowerMeterRawPowerReport =>
      context.become(sense(Some(msg), oldTargetTime, oldGlobalTime) orElse sensorDefault)
    case msg: MonitorTick if target != All =>
      val newTimes = currentTimes(target)
      publishExtPowerReport(msg.muid, msg.target, usageRatio(oldTargetTime, newTimes._1, oldGlobalTime, newTimes._2), power(report), msg.tick)(eventBus)
      context.become(sense(report, newTimes._1, newTimes._2) orElse sensorDefault)
    case msg: MonitorTick if target == All =>
      val power = report match {
        case Some(r) => r.power
        case None => 0.mW
      }
      publishExtPowerReport(msg.muid, msg.target, TargetUsageRatio(1), power, msg.tick)(eventBus)
  }
}
