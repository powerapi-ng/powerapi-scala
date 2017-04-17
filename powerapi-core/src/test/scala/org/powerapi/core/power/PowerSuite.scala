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
package org.powerapi.core.power

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest

class PowerSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() {
    system.terminate()
  }

  "A Power" should "handle a various kind of operations" in {
    val p1 = Power(1.0, "W")
    p1 should (equal(1.W) and equal(1000L.mW) and equal(0.001.kW) and equal(Power(1.0, "nW")))

    p1.value should (
      equal(0.000001.MW.toWatts) and
        equal(1000L.mW.toUnit(WATTS))
      )

    intercept[java.lang.IllegalArgumentException] {
      -5L.kW
    }
    intercept[java.lang.IllegalArgumentException] {
      Double.MaxValue.W
    }
    intercept[java.lang.IllegalArgumentException] {
      Double.NaN.W
    }
    intercept[java.lang.IllegalArgumentException] {
      Double.NegativeInfinity.W
    }
    intercept[java.lang.IllegalArgumentException] {
      Double.PositiveInfinity.W
    }

    1.W should be > 1.mW
    1.W should be < 1.kW
    1.W should be < 1.MW
    50.W should be < 5.kW
    50.W should be > 500.mW

    1.W + 1.W should equal(2.W)
    0.005.kW + 995.W should equal(1L.kW)
    1.MW + 2.W should equal(1000.002.kW)
    1.MW - 400.kW should equal(600000L.W)
    0.W - 0L.MW should equal(0.0.kW)

    val p2 = Power(2 * (Double.MaxValue / 3.0), MILLIWATTS)
    intercept[java.lang.IllegalArgumentException] {
      p2 + (Double.MaxValue / 2.0).mW
    }
    intercept[java.lang.IllegalArgumentException] {
      1.kW - 1.MW
    }

    1.0.kW * 1.0 should equal(0.001.MW)
    8.W * 5 should equal(40.W)
    500.mW * 2 should equal(1L.W)
    0.5.W / 2 should equal(250.mW)
    1000L.MW / 100 should equal(10000.kW)

    MAX(Seq(10.W, 10.mW)) should equal(10.W)
    MIN(Seq(10.W, 10.mW)) should equal(10.mW)
    SUM(Seq(10.W, 10.mW)) should equal(10010.mW)
    MEAN(Seq(10.W, 10.W)) should equal(10.W)
    MEDIAN(Seq(10.W, 10.mW, 12.mW)) should equal(12.mW)

    intercept[java.lang.IllegalArgumentException] {
      1.W * Double.NaN
    }
    intercept[java.lang.IllegalArgumentException] {
      1.W * Double.NegativeInfinity
    }
    intercept[java.lang.IllegalArgumentException] {
      1.W * Double.PositiveInfinity
    }
    intercept[java.lang.IllegalArgumentException] {
      1.W / Double.NaN
    }
    intercept[java.lang.IllegalArgumentException] {
      1.W / Double.NegativeInfinity
    }
    intercept[java.lang.IllegalArgumentException] {
      1.W / Double.PositiveInfinity
    }
    intercept[java.lang.IllegalArgumentException] {
      2.W * Double.MaxValue
    }
    intercept[java.lang.IllegalArgumentException] {
      2.W * -1
    }
    intercept[java.lang.IllegalArgumentException] {
      2.W / 0.0
    }
    intercept[java.lang.IllegalArgumentException] {
      2.W / -2
    }
  }
}

