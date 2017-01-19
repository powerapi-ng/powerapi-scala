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

import scala.collection.BitSet
import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest

class LibpfmCoreSensorConfigurationSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  "The LibpfmCoreSensorConfiguration" should "read correctly the values from a resource file" in {
    val configuration1 = new LibpfmCoreSensorConfiguration(None)
    val configuration2 = new LibpfmCoreSensorConfiguration(Some("libpfm"))

    configuration1.timeout should equal(Timeout(10.seconds))
    configuration1.topology should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    configuration1.events should equal(Set("CPU_CLK_UNHALTED:THREAD_P", "CPU_CLK_UNHALTED:REF_P"))
    configuration1.configuration should equal(BitSet(0, 1, 2, 10))

    configuration2.timeout should equal(Timeout(10.seconds))
    configuration2.topology should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    configuration2.events should equal(Set("event"))
    configuration2.configuration should equal(BitSet(11))
  }
}
