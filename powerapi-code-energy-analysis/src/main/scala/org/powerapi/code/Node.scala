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
import scala.util.control.Breaks

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

      val mergedCallees: Map[String, List[Node]] = (for(node <- nodes) yield node.callees).foldLeft(Map[String, List[Node]]())((acc, map) => acc ++ map.map {
        case (key, value) => key -> (acc.getOrElse(key, List[Node]()) ++ value)
      })

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

  def mergeRawGraph(rawGraph: Node): Unit = {
    if(name == rawGraph.name) {
      if(rawGraph.rawStartTime > 0 && rawGraph.rawStopTime > 0) _executionIntervals :+= (rawGraph.rawStartTime, rawGraph.rawStopTime)
      _callees ++= rawGraph.callees.map {
        case (key, value) => key -> (_callees.getOrElse(key, List[Node]()) ++ value)
      }
    }
    // we merge the graph from the root node
    else _callees += rawGraph.name -> (_callees.getOrElse(rawGraph.name, List[Node]()) :+ rawGraph)
  }

  def addCallee(node: Node): Unit = _callees += node.name -> (callees.getOrElse(node.name, List()) :+ node)

  lazy val fullName: String = parent match {
    case Some(_p) => s"${_p.fullName}.$name"
    case None => s"$name"
  }

  lazy val nbOfCalls: Int = {
    executionIntervals.size
  }

  lazy val executionStartTime: Long = {
    parent match {
      case Some(_p) => _p.executionStartTime
      case None => executionIntervals.minBy(_._1)._1
    }
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
      var _p = this

      while(_p.parent.isDefined) _p = _p.parent.get

      _totalDurations += windowInterval -> _p.overallDuration(windowInterval)
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

      Breaks.breakable {
        var i = 0

        while (true) {
          _rawIntervals = _rawIntervals.sortBy(_._1)
          i = 0

          Breaks.breakable {
            while (i < _rawIntervals.size) {
              val (start, end) = _rawIntervals(i)
              val filteredIntervals = _rawIntervals.diff(List((start, end))).filter(tuple => end >= tuple._1 && end <= tuple._2)
              val _intervals = Seq((start, end)) ++ filteredIntervals
              _rawIntervals = _rawIntervals.diff(_intervals) :+ ((_intervals.minBy(_._1)._1, _intervals.maxBy(_._2)._2))

              if (filteredIntervals.nonEmpty) Breaks.break
              i += 1
            }
          }

          if (i == _rawIntervals.size) Breaks.break
        }
      }

      _rawIntervals
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
            JsArray((for((_, n) <- node.callees) yield n.head.asInstanceOf[Node].toJson(SunburstChartForEnergyFormat)).toVector ++ Vector(
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
