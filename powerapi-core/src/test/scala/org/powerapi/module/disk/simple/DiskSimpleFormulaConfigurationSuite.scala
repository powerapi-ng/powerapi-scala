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
package org.powerapi.module.disk.simple

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest

class DiskSimpleFormulaConfigurationSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  trait Formulae {
    val formulae1 = Map[String, Map[String, Seq[PieceWiseFunction]]](
      "sda" -> Map(
        "read" -> Seq(PieceWiseFunction(Condition("<= 78966784"), Seq(0.00, 1.01e-07)), PieceWiseFunction(Condition("> 78966784"), Seq(7.62, 1.72e-10))),
        "write" -> Seq(PieceWiseFunction(Condition("<= 66674688"), Seq(0.00, 1.13e-07)), PieceWiseFunction(Condition("> 66674688"), Seq(8.33, 1.79e-09)))
      ),
      "sdb" -> Map(
        "read" -> Seq(PieceWiseFunction(Condition("<= 10"), Seq(0.00, 0.003)), PieceWiseFunction(Condition("> 10"), Seq(2, 0.15))),
        "write" -> Seq(PieceWiseFunction(Condition("<= 5"), Seq(0.00, 0.001)), PieceWiseFunction(Condition("> 5"), Seq(3, 0.25)))
      )
    )
    val formulae2 = Map[String, Map[String, Seq[PieceWiseFunction]]](
      "sdb" -> Map(
        "read" -> Seq(PieceWiseFunction(Condition("<= 100"), Seq(0.00, 0.0013)), PieceWiseFunction(Condition("> 100"), Seq(2.13, 0.28))),
        "write" -> Seq(PieceWiseFunction(Condition("<= 1000"), Seq(0.00, 0.0016)), PieceWiseFunction(Condition("> 1000"), Seq(3.15, 0.40)))
      )
    )
  }

  "A Condition case class" should "represent correctly a condition for a piecewise function" in {
    Condition("<", 10).test(-10) should equal(true)
    Condition("<", 10).test(10) should equal(false)
    Condition("<=", 10).test(-10) should equal(true)
    Condition("<=", 10).test(10) should equal(true)
    Condition("<=", 10).test(11) should equal(false)
    Condition(">", 10).test(11) should equal(true)
    Condition(">", 10).test(9) should equal(false)
    Condition(">=", 10).test(11) should equal(true)
    Condition(">=", 10).test(10) should equal(true)
    Condition(">=", 10).test(9) should equal(false)

    Condition(">=", 0, Some("&&"), Some("<="), Some(10)).test(10) should equal(true)
    Condition(">=", 0, Some("&&"), Some("<="), Some(10)).test(11) should equal(false)
    Condition("<=", 0, Some("||"), Some(">="), Some(100)).test(-10) should equal(true)
    Condition("<=", 0, Some("||"), Some(">="), Some(100)).test(100) should equal(true)
    Condition("<=", 0, Some("||"), Some(">="), Some(100)).test(10) should equal(false)

    Condition("> 0") should equal(Condition(">", 0))
    Condition(">0&&<=10") should equal(Condition(">", 0, Some("&&"), Some("<="), Some(10)))
    Condition(">0||>100") should equal(Condition(">", 0, Some("||"), Some(">"), Some(100)))
    Condition(">0||") should equal(Condition(">", 0))
    Condition(">0||<") should equal(Condition(">", 0))
    Condition(">0<100") should equal(Condition(">", 0))
  }

  "The DiskSimpleFormulaConfiguration" should "read correctly the values from a resource file" in new Formulae {
    val configuration1 = new DiskSimpleFormulaConfiguration(None)
    val configuration2 = new DiskSimpleFormulaConfiguration(Some("disk-test"))

    configuration1.formulae should contain theSameElementsAs formulae1
    configuration1.interval should equal(1.second)
    configuration2.formulae should contain theSameElementsAs formulae2
    configuration2.interval should equal(250.millis)
  }
}
