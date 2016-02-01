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
package org.powerapi.module.extPMeter.g5k

import org.powerapi.core.{MessageBus, ExternalPMeter}
import org.powerapi.core.power._
import org.powerapi.module.extPMeter.ExtPMeterChannel.publishExternalPMeterPower
import org.apache.logging.log4j.LogManager
import scala.concurrent.duration.{FiniteDuration, DurationInt}
import spray.json._
import DefaultJsonProtocol._


case class PowerR(integrated: Double, timestamp: Long, `type`: String, unit: String, value: Double)
case class Probe(power: PowerR)

object PowerJsonProtocol extends DefaultJsonProtocol {
  implicit val powerrFormat = jsonFormat5(PowerR)
  implicit def probeFormat = jsonFormat1(Probe)
}

/**
 * Powermeter (Omegawatt) from Grid5000 Lyon site special helper.
 * @see https://intranet.grid5000.fr/supervision/lyon/wattmetre/
 *
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
class G5kPMeter(probeUrl: String, interval: FiniteDuration) extends ExternalPMeter {

  @volatile private var running = true
  @volatile private var thread: Option[java.lang.Thread] = None

  protected var eventBus: Option[MessageBus] = None

  private val log = LogManager.getLogger

  def init(bus: MessageBus): Unit = {
    eventBus = Some(bus)
    running = true
  }

  def start(): Unit = {
    thread match {
      case None => {
        val threadInst = new java.lang.Thread {
          override def run(): Unit = {
            while(running) {
              readRealTime() match {
                case Some(rtValue) if eventBus.get != None => publishExternalPMeterPower(Power(rtValue.value, rtValue.unit))(eventBus.get)
                case _ => {}
              }
              Thread.sleep(interval.toMillis)
            }
          }
        }

        threadInst.start()
        thread = Some(threadInst)
      }
      case _ => log.debug("Connexion already established")
    }
  }

  def stop(): Unit = {
    running = false

    thread match {
      case Some(thr) => thr.join(1.seconds.toMillis)
      case _ => log.debug("Call the method start() before stopping.")
    }

    thread = None
  }
  
  private def readRealTime(): Option[PowerR] = {
    import PowerJsonProtocol._
    val json = scala.io.Source.fromURL(probeUrl).mkString.parseJson
    val result = json.asJsObject.fields.head._2.convertTo[Probe]
    Some(result.power)
  }
}
