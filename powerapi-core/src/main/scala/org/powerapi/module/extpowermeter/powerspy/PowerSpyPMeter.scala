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
package org.powerapi.module.extpowermeter.powerspy

import scala.concurrent.duration.{DurationInt, FiniteDuration}

import fr.inria.powerspy.core.PowerSpy
import org.apache.logging.log4j.LogManager
import org.powerapi.core.power._
import org.powerapi.core.{ExternalPMeter, MessageBus}
import org.powerapi.module.extpowermeter.ExtPowerMeterChannel.publishPowerSpyRawPowerReport

/**
  * PowerSpy special helper.
  *
  * @see http://www.alciom.com/en/products/powerspy2-en-gb-2.html
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class PowerSpyPMeter(mac: String, interval: FiniteDuration) extends ExternalPMeter {

  private val log = LogManager.getLogger
  protected var eventBus: Option[MessageBus] = None
  @volatile private var running = true
  @volatile private var thread: Option[java.lang.Thread] = None
  @volatile private var powerspy: Option[PowerSpy] = None

  def init(bus: MessageBus): Unit = {
    powerspy = PowerSpy.init(mac)
    eventBus = Some(bus)
    running = true
  }

  def start(): Unit = {
    powerspy match {
      case Some(pSpy) =>
        pSpy.start()

        log.debug("Trying to establish the connexion ...")
        while (!pSpy.startRealTime(interval)) {
          log.error("Failed. Retrying.")
          java.lang.Thread.sleep(interval.toMillis)
        }
        log.debug("Connexion established.")

        thread match {
          case None =>
            val threadInst = new java.lang.Thread {
              override def run(): Unit = {
                while (running) {
                  pSpy.readRealTime() match {
                    case Some(rtValue) if eventBus.isDefined =>
                      publishPowerSpyRawPowerReport(rtValue.power.W)(eventBus.get)
                    case _ =>

                  }
                }
              }
            }

            threadInst.start()
            thread = Some(threadInst)
          case _ =>
            log.debug("Connexion already established")
        }
      case _ =>
        log.error("Problem for establishing the connexion with PowerSpy")
    }
  }

  def stop(): Unit = {
    powerspy match {
      case Some(pSpy) =>
        running = false

        thread match {
          case Some(thr) =>
            thr.join(1.seconds.toMillis)
          case _ =>
            log.debug("Call the method start() before stopping.")
        }

        pSpy.stopRealTime()
        pSpy.stop()
        PowerSpy.deinit()

        thread = None
        powerspy = None
      case _ =>
        log.error("Connexion with PowerSpy is not established.")
    }
  }
}
