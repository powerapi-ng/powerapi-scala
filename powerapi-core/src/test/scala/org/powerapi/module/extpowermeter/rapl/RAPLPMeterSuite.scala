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

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.module.extpowermeter.ExtPowerMeterChannel.{ExtPowerMeterRawPowerReport, subscribeRAPLRawPowerReport}

class RAPLPMeterSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A RAPLPmeter" should "should collect values from the RAPL cpu sensor and publish raw power reports" ignore new Bus {
    val conf = new RAPLHelperConfiguration
    val pMeter = new RAPLPMeter(conf.msrPath, conf.cpuInfoPath, conf.supportedArchis, 1.seconds)
    subscribeRAPLRawPowerReport(eventBus)(testActor)

    pMeter.init(eventBus)
    pMeter.start()

    val messages = receiveWhile(max = 10.seconds) {
      case _: ExtPowerMeterRawPowerReport => true
    }
    messages should not be 'empty

    pMeter.stop()
  }
}
