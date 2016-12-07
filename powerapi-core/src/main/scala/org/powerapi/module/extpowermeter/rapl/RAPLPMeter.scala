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
package org.powerapi.module.extpowermeter.rapl

import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import org.powerapi.core.power.Power
import org.powerapi.core.{ExternalPMeter, MessageBus}
import org.powerapi.module.extpowermeter.ExtPowerMeterChannel.publishRAPLRawPowerReport

/**
  * RAPL meter.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class RAPLPMeter(msrPath: String, cpuInfoPath: String, supportedArchis: Map[Int, String], interval: FiniteDuration) extends ExternalPMeter {

  private val log = Logger(classOf[RAPLPMeter])
  protected var eventBus: Option[MessageBus] = None
  @volatile private var running = true
  @volatile private var thread: Option[java.lang.Thread] = None
  @volatile private var raplHelper: Option[RAPLHelper] = None

  def init(bus: MessageBus): Unit = {
    raplHelper = Some(new RAPLHelper(msrPath, cpuInfoPath, supportedArchis))
    eventBus = Some(bus)
    running = true
  }

  def start(): Unit = {
    raplHelper match {
      case Some(rapl) =>
        thread match {
          case None =>
            val threadInst = new java.lang.Thread {
              var old = rapl.getRAPLEnergy

              override def run(): Unit = {
                while (running) {
                  val now = rapl.getRAPLEnergy
                  publishRAPLRawPowerReport(Power.fromJoule(now - old, interval))(eventBus.get)
                  old = now
                  Thread.sleep(interval.toMillis)
                }
              }
            }

            threadInst.start()
            thread = Some(threadInst)
          case _ =>
            log.debug("Thread already created")
        }
      case _ =>
        log.error("Problem during the RAPLHelper instantiation")
    }
  }

  def stop(): Unit = {
    raplHelper match {
      case Some(rapl) =>
        running = false

        thread match {
          case Some(thr) =>
            thr.join(1.seconds.toMillis)
          case _ =>
            log.debug("Call the method start() before stopping.")
        }

        thread = None
        raplHelper = None
      case _ =>
        log.error("Problem during the RAPLHelper instantiation")
    }
  }
}
