/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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
package org.powerapi.code

import java.util.concurrent.TimeUnit
import akka.util.Timeout
import org.powerapi.core.{Configuration, ConfigValue}
import scala.concurrent.duration.{FiniteDuration, DurationLong}
import scala.concurrent.duration.SECONDS
import spray.json._
import scalaz.Scalaz._

object NodeConfiguration extends Configuration {
  lazy val timeout: Timeout = load { _.getDuration("powerapi.actors.timeout", TimeUnit.MILLISECONDS) } match {
    case ConfigValue(value) => Timeout(value.toLong.milliseconds)
    case _ => Timeout(15l.seconds)
  }
}

/**
 * Helps to represent a graph built from traces of an instrumented program.
 * All timestamps are in nanoseconds.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object GraphUtil {

  def buildCallgraph(node: Node): Node = {
    val monitoringFrequency = node.monitoringFrequency
    val powers = node.powers
    val current = Node(node.name, node.monitoringFrequency, node.powers)

    val nodes: Map[String, List[Node]] = for((name, nodes) <- node.callees) yield {
      val n = Node(name, monitoringFrequency, powers)

      val mergedCallees: Map[String, List[Node]] = nodes.map(_.callees).reduce(_ |+| _)

      n.callees = mergedCallees
      n.executionIntervals = nodes.map(rawNode => (rawNode.rawStartTime, rawNode.rawStopTime))

      (name, List(buildCallgraph(n)))
    }

    for((_, callees) <- nodes) callees.foreach(_.parent = Some(current))
    current.executionIntervals = node.executionIntervals
    current.callees = nodes
    current
  }
}

case class Node(name: String, monitoringFrequency: FiniteDuration, powers: List[Double]) {
  private[this] var _parent: Option[Node] = None
  private[this] var _rawStartTime = -1l
  private[this] var _rawStopTime = -1l
  private[this] var _executionIntervals = scala.collection.mutable.ListBuffer[(Long, Long)]()
  private[this] val _callees = scala.collection.mutable.Map[String, List[Node]]()

  private[this] val _effectiveDurations = scala.collection.mutable.Map[(Long, Long), FiniteDuration]()
  private[this] val _totalDurations = scala.collection.mutable.Map[(Long, Long), FiniteDuration]()
  private[this] val _overallDurations = scala.collection.mutable.Map[(Long, Long), FiniteDuration]()
  private[this] val _durations = scala.collection.mutable.Map[(Long, Long), (FiniteDuration, FiniteDuration)]()

  def parent: Option[Node] = _parent
  def callees: Map[String, List[Node]] = _callees.toMap
  def rawStartTime: Long = _rawStartTime
  def rawStopTime: Long = _rawStopTime
  def executionIntervals: List[(Long, Long)] = _executionIntervals.toList

  def parent_= (parent: Option[Node]): Unit = _parent = parent
  def rawStartTime_= (startTime: Long): Unit = _rawStartTime = startTime
  def rawStopTime_= (stopTime: Long): Unit = _rawStopTime = stopTime
  def executionIntervals_= (intervals: List[(Long, Long)]): Unit = {
    _executionIntervals.clear()
    _executionIntervals ++= intervals
  }
  def callees_= (callees: Map[String, List[Node]]) = {
    _callees.clear()
    _callees ++= callees
  }

  def addCallee(node: Node): Unit = _callees += node.name -> (callees.getOrElse(node.name, List()) :+ node)

  lazy val fullName: String = parent match {
    case Some(_p) => s"${_p.fullName}.$name"
    case None => s"$name"
  }

  lazy val nbOfCalls: Int = {
    executionIntervals.size
  }

  lazy val root: Node = {
    var _p = this

    while(_p.parent.isDefined) _p = _p.parent.get

    _p
  }

  lazy val executionStartTime: Long = {
    root.allStartTimes.min
  }

  lazy val executionStopTime: Long = {
    root.allStopTimes.max
  }

  lazy val duration: FiniteDuration = {
    (for(i <- powers.indices) yield {
      val startInterval = executionStartTime + (i * monitoringFrequency.toNanos)
      val stopInterval = startInterval + monitoringFrequency.toNanos

      effectiveDuration(startInterval, stopInterval)
    }).foldLeft(0l.nano)((acc, duration) => acc + duration)
  }

  lazy val energy: Double = {
    (for(i <- powers.indices) yield {
      val startInterval = executionStartTime + (i * monitoringFrequency.toNanos)
      val stopInterval = startInterval + monitoringFrequency.toNanos
      val selfDuration = effectiveDuration(startInterval, stopInterval).toNanos
      val allDuration = totalDuration(startInterval, stopInterval).toNanos

      if(allDuration == 0) 0.0
      else {
        val ratio = selfDuration / allDuration.toDouble
        powers(i) * monitoringFrequency.toUnit(SECONDS) * ratio
      }
    }).sum
  }

  lazy val totalEnergy: Double = {
    parent match {
      case Some(_p) => _p.totalEnergy
      case None => overallEnergy
    }
  }

  lazy val totalDuration: FiniteDuration = {
    parent match {
      case Some(_p) => _p.totalDuration
      case None => {
        (for(i <- powers.indices) yield {
          val startInterval = executionStartTime + (i * monitoringFrequency.toNanos)
          val stopInterval = startInterval + monitoringFrequency.toNanos

          overallDuration(startInterval, stopInterval)
        }).foldLeft(0l.nanos)((acc, duration) => acc + duration)
      }
    }
  }

  def totalDuration(windowInterval: (Long, Long)): FiniteDuration = {
    if(!_totalDurations.contains(windowInterval)) {
      _totalDurations += windowInterval -> root.overallDuration(windowInterval)
    }

    _totalDurations(windowInterval)
  }

  def effectiveDuration(windowInterval: (Long, Long)): FiniteDuration = {
    if(!_effectiveDurations.contains(windowInterval)) {
      val (self, other) = durations(windowInterval)

      _effectiveDurations(windowInterval) = {
        if (self > other) self - other
        else 0l.nanos
      }
    }

    _effectiveDurations(windowInterval)
  }

  def updateStopTime(stop: Long): Unit = {
    executionIntervals = executionIntervals.map(interval => if(interval._2 == -1) (interval._1, stop) else (interval._1, interval._2))
    callees.foreach { case (_, nodes) => nodes.head.updateStopTime(stop) }
  }

  private[Node] lazy val overallEnergy: Double = {
    energy + callees.foldLeft(0d) {
      case (acc, (_, nodes)) => acc + nodes.head.overallEnergy
    }
  }

  private[Node] def overallDuration(windowInterval: (Long, Long)): FiniteDuration = {
    if(!_overallDurations.contains(windowInterval)) {
      _overallDurations += windowInterval ->
        (effectiveDuration(windowInterval) + callees.foldLeft(0l.nano) {
          case (acc, (_, nodes)) => acc + nodes.head.overallDuration(windowInterval)
        })
    }

    _overallDurations(windowInterval)
  }

  private[code] def durations(windowTime: (Long, Long)): (FiniteDuration, FiniteDuration) = {

    def _computeIntervals(rawIntervals: Seq[(Long, Long)]): Seq[(Long, Long)] = {
      var _rawIntervals = (Seq() ++ rawIntervals).sortBy(_._1)
      val _intervals = scala.collection.mutable.ListBuffer[(Long, Long)]()

      var (start, end): (Long, Long) = if(_rawIntervals.headOption.nonEmpty) _rawIntervals.head else (-1, -1)
      _rawIntervals = if(_rawIntervals.nonEmpty) _rawIntervals.tail else Seq()

      while(_rawIntervals.nonEmpty) {
        val (t1, t2): (Long, Long) = _rawIntervals.head
        if(t1 > start && t1 < end) {
          end = t2
        }
        else {
          _intervals += ((start, end))
          if(_rawIntervals.tail.isEmpty) _intervals += ((t1, t2))
          start = t1
          end = t2
        }

        _rawIntervals = _rawIntervals.tail
      }

      if(_intervals.nonEmpty) _intervals else Seq((start, end))
    }

    def _computeDuration(rawIntervals: Seq[(Long, Long)]): FiniteDuration = {
      (for((start, end) <- rawIntervals) yield {
        if(windowTime._1 > windowTime._2 || start > windowTime._2 || start > end || windowTime._1 > end) 0l.nanos
        else {
          val endTmp = if (end <= windowTime._2) end else windowTime._2
          val startTmp = if (start >= windowTime._1) start else windowTime._1

          (endTmp - startTmp).nanos
        }
      }).foldLeft(0l.nanos)((acc, duration) => acc + duration)
    }

    if(!_durations.contains(windowTime)) {
      val self = _computeIntervals(executionIntervals)
      val other = _computeIntervals(callees.foldLeft(Seq[(Long, Long)]()) {
        case (acc, (_, nodes)) => acc ++ nodes.flatMap(_.executionIntervals)
      })

      _durations += windowTime -> (_computeDuration(self), _computeDuration(other))
    }

    _durations(windowTime)
  }

  private def allStartTimes: Seq[Long] = {
    executionIntervals.map(_._1) ++ callees.foldLeft(Seq[Long]()) { case (acc, (_, nodes)) => acc ++ nodes.head.allStartTimes }
  }

  private def allStopTimes: Seq[Long] = {
    executionIntervals.map(_._2) ++ callees.foldLeft(Seq[Long]()) { case (acc, (_, nodes)) => acc ++ nodes.head.allStopTimes }
  }
}

object JSONProtocol extends DefaultJsonProtocol {

  object NodeFormat extends RootJsonFormat[Node] {

    def write(node: Node): JsObject = {
      JsObject(
        "sunburst" -> SunburstChartForEnergyFormat.write(node),
        "streamgraph" -> StreamgraphChartForPowerFormat.write(node).fields("data")
      )
    }

    def read(value: JsValue): Node = {
      throw new Exception("NodeFormat is only used to export json")
    }
  }

  private[JSONProtocol] object SunburstChartForEnergyFormat extends RootJsonFormat[Node] {

    def write(node: Node): JsObject = {
      val name = node.name
      val selfEnergy = node.energy
      val selfDuration = node.duration
      val totalEnergy = node.totalEnergy
      val totalDuration = node.totalDuration
      val nbOfCalls = node.nbOfCalls

      if(node.callees.nonEmpty) {
        JsObject(
          "name" -> JsString(name),
          "selfEnergy" -> JsNumber(selfEnergy),
          "selfDuration" -> JsNumber(selfDuration.toNanos),
          "totalEnergy" -> JsNumber(totalEnergy),
          "totalDuration" -> JsNumber(totalDuration.toNanos),
          "nbOfCalls" -> JsNumber(nbOfCalls),
          "children" ->
            JsArray((for((_, n) <- node.callees) yield n.head.toJson(SunburstChartForEnergyFormat)).toVector ++ Vector(
              JsObject(
                "name" -> JsString("self"),
                "selfEnergy" -> JsNumber(selfEnergy),
                "selfDuration" -> JsNumber(selfDuration.toNanos),
                "totalEnergy" -> JsNumber(totalEnergy),
                "totalDuration" -> JsNumber(totalDuration.toNanos)
              )
            ))
        )
      }

      else {
        JsObject(
          "name" -> JsString(name),
          "selfEnergy" -> JsNumber(selfEnergy),
          "selfDuration" -> JsNumber(selfDuration.toNanos),
          "totalEnergy" -> JsNumber(totalEnergy),
          "totalDuration" -> JsNumber(totalDuration.toNanos),
          "nbOfCalls" -> JsNumber(nbOfCalls)
        )
      }
    }

    def read(value: JsValue): Node = {
      throw new Exception("SunburstChartForEnergyFormat is only used to export json")
    }
  }

  private[JSONProtocol] object StreamgraphChartForPowerFormat extends RootJsonFormat[Node] {

    def powers(node: Node): Vector[JsObject] = {
      (for(i <- node.powers.indices) yield {
        val startInterval = node.executionStartTime + (i * node.monitoringFrequency.toNanos)
        val stopInterval = startInterval + node.monitoringFrequency.toNanos
        val selfDuration = node.effectiveDuration(startInterval, stopInterval).toNanos
        val allDuration = node.totalDuration(startInterval, stopInterval).toNanos
        val power = {
          if (allDuration == 0) 0.0
          else {
            val ratio = selfDuration / allDuration.toDouble
            node.powers(i) * ratio
          }
        }

        JsObject(
          "name" -> JsString(node.fullName),
          "x" -> JsNumber(i),
          "y" -> JsNumber(power)
        )
      }).toVector ++ (for(callee <- node.callees.values) yield StreamgraphChartForPowerFormat.powers(callee.head)).flatten.toVector
    }

    def write(node: Node): JsObject = {
      JsObject(
        "data" -> JsArray(powers(node))
      )
    }

    def read(value: JsValue): Node = {
      throw new Exception("StreamgraphChartForPowerFormat is only used to export json")
    }
  }
}
