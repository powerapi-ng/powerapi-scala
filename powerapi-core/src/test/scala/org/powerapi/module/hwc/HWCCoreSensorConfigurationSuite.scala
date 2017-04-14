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

import akka.util.Timeout
import org.powerapi.UnitTest

import scala.collection.BitSet
import scala.concurrent.duration.DurationInt

class HWCCoreSensorConfigurationSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  "The HWCCoreSensorConfiguration" should "read correctly the values from a resource file" in {
    val configuration1 = new HWCCoreSensorConfiguration(None)
    val configuration2 = new HWCCoreSensorConfiguration(Some("hwc"))

    configuration1.events should equal(List("CPU_CLK_UNHALTED_CORE:FIXC1", "CPU_CLK_UNHALTED_REF:FIXC2"))

    configuration2.events should equal(List("event"))
  }
}
