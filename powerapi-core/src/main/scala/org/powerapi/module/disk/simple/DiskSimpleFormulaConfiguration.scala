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

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import org.powerapi.core.{ConfigValue, Configuration}

import collection.JavaConverters._
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration

case class Condition(cmpOp1: String, value1: Double, binOp: Option[String] = None, cmpOp2: Option[String] = None, value2: Option[Double] = None) {

  def test(x: Double): Boolean = {
    val part1 = cmpOp1 match {
      case "<=" => x <= value1
      case ">=" => x >= value1
      case "<" => x < value1
      case ">" => x > value1
      case _ => false
    }

    val part2 = {
      if (cmpOp2.isDefined && value2.isDefined) {
        cmpOp2.get match {
          case "<=" => x <= value2.get
          case ">=" => x >= value2.get
          case "<" => x < value2.get
          case ">" => x > value2.get
          case _ => false
        }
      }
      else true
    }

    if (binOp.isDefined) {
      binOp.get match {
        case "&&" => part1 && part2
        case "||" => part1 || part2
      }
    }
    else part1
  }
}

object Condition {
  val ConditionR = """(<=|>=|<|>)\s*(\d+\.?\d*)\s*((&&|\|\|)\s*(<=|>=|<|>)\s*(\d+\.?\d*))?""".r

  def apply(str: String): Condition = str match {
    case ConditionR(cmpOp1, value1, _, null, null, null) => Condition(cmpOp1, value1.toDouble)
    case ConditionR(cmpOp1, value1, _, binOp, cmpOp2, value2) => Condition(cmpOp1, value1.toDouble, Some(binOp), Some(cmpOp2), Some(value2.toDouble))
    case _ => Condition(">", 0)
  }
}

case class PieceWiseFunction(condition: Condition, coeffs: Seq[Double])

/**
  * Main configuration.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class DiskSimpleFormulaConfiguration(prefix: Option[String]) extends Configuration(prefix) {
  /**
    * Disk power models, one per ssd disk.
    */
  lazy val formulae: Map[String, Map[String, Seq[PieceWiseFunction]]] = load { conf =>
    (for (item: Config <- conf.getConfigList(s"${configurationPath}powerapi.disk.formulae").asScala) yield {
      val coefficientsItem = item.getConfig("models")
      item.getString("name") -> Seq(
        "read" -> coefficientsItem.getConfigList("read").asScala.map(config => PieceWiseFunction(Condition(config.getString("condition")), config.getDoubleList("coeffs").asScala.map(_.toDouble))).toSeq,
        "write" -> coefficientsItem.getConfigList("write").asScala.map(config => PieceWiseFunction(Condition(config.getString("condition")), config.getDoubleList("coeffs").asScala.map(_.toDouble))).toSeq
      ).toMap
    }).toMap
  } match {
    case ConfigValue(values) => values
    case _ => Map()
  }

  lazy val interval: FiniteDuration = load {
    _.getDuration(s"${configurationPath}powerapi.disk.interval", TimeUnit.NANOSECONDS)
  } match {
    case ConfigValue(value) => value.nanoseconds
    case _ => 1l.seconds
  }
}
