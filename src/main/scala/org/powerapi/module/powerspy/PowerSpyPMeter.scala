/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2014 Inria, University of Lille 1.
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
package org.powerapi.module.powerspy

import org.powerapi.core.{MessageBus, ExternalPMeter, Configuration}

/**
 * PowerSpy special helper.
 * @see http://www.alciom.com/en/products/powerspy2-en-gb-2.html
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class PowerSpyPMeter(eventBus: MessageBus) extends ExternalPMeter with Configuration {
  import org.powerapi.core.ConfigValue
  import org.powerapi.module.PowerUnit
  import org.powerapi.module.powerspy.PowerSpyChannel.publishPowerSpyPower
  import fr.inria.powerspy.core.PowerSpy
  import org.apache.logging.log4j.LogManager
  import scala.concurrent.duration.DurationDouble

  @volatile private var running = true
  @volatile private var thread: Option[java.lang.Thread] = None
  @volatile private var pSpyOption: Option[PowerSpy] = None

  private val log = LogManager.getLogger
  lazy val mac = load { _.getString("powerspy.mac") } match {
    case ConfigValue(address) => address
    case _ => ""
  }

  def init(): Unit = {
    pSpyOption = PowerSpy(mac, 3.seconds)
  }

  def start(): Unit = {
    pSpyOption match {
      case Some(pSpy) => {
        pSpy.start()

        while(!pSpy.startRealTime(1.seconds)) java.lang.Thread.sleep(1.seconds.toMillis)

        thread match {
          case None => {
            val threadInst = new java.lang.Thread {
              override def run(): Unit = {
                while(running) {
                  pSpy.readRealTime() match {
                    case Some(rtValue) => publishPowerSpyPower(rtValue.power, PowerUnit.W)(eventBus)
                    case _ => {}
                  }
                }
              }
            }

            threadInst.start()
            thread = Some(threadInst)
          }
          case _ => log.debug("Connexion already established")
        }
      }
      case _ => log.error("Problem for establishing the connexion with PowerSpy")
    }
  }

  def stop(): Unit = {
    pSpyOption match {
      case Some(pSpy) => {
        running = false

        thread match {
          case Some(thr) => thr.join(1.seconds.toMillis)
          case _ => log.debug("Call the method start() before stopping.")
        }

        pSpy.stopRealTime()
        pSpy.stop()
        pSpy.connexion.close()

        running = true
        thread = None
        pSpyOption = None
      }
      case _ => log.error("Connexion with PowerSpy is not established.")
    }
  }
}
