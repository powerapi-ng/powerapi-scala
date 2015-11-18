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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import scala.concurrent.duration.{DurationLong, SECONDS}

class NodeSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("NodeSuite"))

  val timeout = Timeout(1l.seconds)
  val eps = 1e-3

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A Graph" should "be correctly built" in {
    val startTime = System.nanoTime

    val frequency = 50l.millis
    val scaling = frequency.toUnit(SECONDS)
    val powers = (1 to 25).map(_.toDouble).toList

    val rawGraph = Node("main", frequency, powers)
    val node1 = Node("a", frequency, powers)
    val node2 = Node("b", frequency, powers)
    val subNode1_1 = Node("b", frequency, powers)
    val subNode1_2 = Node("c", frequency, powers)
    val subNode2_1 = Node("c", frequency, powers)

    rawGraph.parent = None
    rawGraph.rawStartTime = startTime
    rawGraph.executionIntervals = List((rawGraph.rawStartTime, rawGraph.rawStopTime))

    node1.parent = Some(rawGraph)
    node1.rawStartTime = startTime + 100l.millis.toNanos
    node1.rawStopTime = startTime + 140l.millis.toNanos

    node2.parent = Some(rawGraph)
    node2.rawStartTime = startTime + 180l.millis.toNanos
    node2.rawStopTime = startTime + 800l.millis.toNanos

    subNode1_1.parent = Some(node1)
    subNode1_1.rawStartTime = startTime + 110l.millis.toNanos
    subNode1_1.rawStopTime = startTime + 120l.millis.toNanos

    subNode1_2.parent = Some(node1)
    subNode1_2.rawStartTime = startTime + 125l.millis.toNanos
    subNode1_2.rawStopTime = startTime + 135l.millis.toNanos

    subNode2_1.parent = Some(node2)
    subNode2_1.rawStartTime = startTime + 200l.millis.toNanos
    subNode2_1.rawStopTime = startTime + 500l.millis.toNanos

    node1.addCallee(subNode1_1)
    node1.addCallee(subNode1_2)
    node2.addCallee(subNode2_1)
    rawGraph.addCallee(node2)
    rawGraph.addCallee(node1)

    val graph = GraphUtil.buildCallgraph(rawGraph)

    graph.updateStopTime(startTime + 1000l.millis.toNanos)

    graph.callees.keys should contain only("a", "b")
    graph.callees("a").head.callees.keys should contain only("b", "c")
    graph.callees("b").head.callees.keys should contain only("c")

    graph.fullName should equal("main")
    graph.callees("a").head.fullName should equal("main.a")
    graph.callees("b").head.fullName should equal("main.b")
    graph.callees("a").head.callees("b").head.fullName should equal("main.a.b")
    graph.callees("a").head.callees("c").head.fullName should equal("main.a.c")
    graph.callees("b").head.callees("c").head.fullName should equal("main.b.c")

    graph.executionStartTime should equal(startTime)
    graph.callees("a").head.executionStartTime should equal(startTime)
    graph.callees("b").head.executionStartTime should equal(startTime)
    graph.callees("a").head.callees("b").head.executionStartTime should equal(startTime)
    graph.callees("a").head.callees("c").head.executionStartTime should equal(startTime)
    graph.callees("b").head.callees("c").head.executionStartTime should equal(startTime)

    graph.executionStopTime should equal(startTime + 1000l.millis.toNanos)
    graph.callees("a").head.executionStopTime should equal(startTime + 1000l.millis.toNanos)
    graph.callees("b").head.executionStopTime should equal(startTime + 1000l.millis.toNanos)
    graph.callees("a").head.callees("b").head.executionStopTime should equal(startTime + 1000l.millis.toNanos)
    graph.callees("a").head.callees("c").head.executionStopTime should equal(startTime + 1000l.millis.toNanos)
    graph.callees("b").head.callees("c").head.executionStopTime should equal(startTime + 1000l.millis.toNanos)

    graph.effectiveDuration(startTime, startTime + 1000l.millis.toNanos) should
      (be >= 0l.nanos and equal((1000 - ((140 - 100) + (800 - 180))).toLong.millis))
    graph.callees("a").head.effectiveDuration(startTime, startTime + 1000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((140 - 100) - ((120 - 110) + (135 - 125))).toLong.millis))
    graph.callees("a").head.callees("b").head.effectiveDuration(startTime, startTime + 1000l.millis.toNanos) should
      (be >= 0l.nanos and equal((120 - 110).toLong.millis))
    graph.callees("a").head.callees("c").head.effectiveDuration(startTime, startTime + 1000l.millis.toNanos) should
      (be >= 0l.nanos and equal((135 - 125).toLong.millis))
    graph.callees("b").head.effectiveDuration(startTime, startTime + 1000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((800 - 180) - (500 - 200)).toLong.millis))
    graph.callees("b").head.callees("c").head.effectiveDuration(startTime, startTime + 1000l.millis.toNanos) should
      (be >= 0l.nanos and equal((500 - 200).toLong.millis))

    graph.duration should
      (be >= 0l.nanos and equal((50 + 50 + (50 - 40) + (50 - 20) + 4 * 50).toLong.millis))
    graph.callees("a").head.duration should
      (be >= 0l.nanos and equal((0 + 0 + (40 - 10 - 10)).toLong.millis))
    graph.callees("a").head.callees("b").head.duration should
      (be >= 0l.nanos and equal((0 + 0 + 10).toLong.millis))
    graph.callees("a").head.callees("c").head.duration should
      (be >= 0l.nanos and equal((0 + 0 + 10).toLong.millis))
    graph.callees("b").head.duration should
      (be >= 0l.nanos and equal((0 + 0 + 0 + 20 + 6 * 50).toLong.millis))
    graph.callees("b").head.callees("c").head.duration should
      (be >= 0l.nanos and equal((0 + 0 + 0 + 0 + (6 * 50)).toLong.millis))

    val totalDuration = graph.duration.plus(graph.callees("a").head.duration).plus(graph.callees("a").head.callees("b").head.duration)
                        .plus(graph.callees("a").head.callees("c").head.duration).plus(graph.callees("b").head.duration)
                        .plus(graph.callees("b").head.callees("c").head.duration)

    graph.totalDuration should equal(totalDuration)
    graph.callees("a").head.totalDuration should equal(totalDuration)
    graph.callees("b").head.totalDuration should equal(totalDuration)
    graph.callees("a").head.callees("b").head.totalDuration should equal(totalDuration)
    graph.callees("a").head.callees("c").head.totalDuration should equal(totalDuration)
    graph.callees("b").head.callees("c").head.totalDuration should equal(totalDuration)

    graph.energy should
      equal((1 * scaling * (50/50.toDouble) + 2 * scaling *  (50/50.toDouble) + 3 * scaling * (10/50.toDouble) + 4 * scaling * (30/50.toDouble) + powers.slice(16, 20).map(_ * scaling).sum) +- eps)
    graph.callees("a").head.energy should
      equal((1 * scaling * (0/50.toDouble) + 2 * scaling * (0/50.toDouble) + 3 * scaling * (20/50.toDouble)) +- eps)
    graph.callees("a").head.callees("b").head.energy should
      equal((1 * scaling * (0/50.toDouble) + 2 * scaling * (0/50.toDouble) + 3 * scaling * (10/50.toDouble)) +- eps)
    graph.callees("a").head.callees("c").head.energy should
      equal((1 * scaling * (0/50.toDouble) + 2 * scaling * (0/50.toDouble) + 3 * scaling * (10/50.toDouble)) +- eps)
    graph.callees("b").head.energy should
      equal((1 * scaling * (0/50.toDouble) + 2 * scaling * (0/50.toDouble) + 3 * scaling * (0/50.toDouble) + 4 * scaling * (20/50.toDouble) + powers.slice(10, 16).map(_ * scaling).sum) +- eps)
    graph.callees("b").head.callees("c").head.energy should
      equal((1 * scaling * (0/50.toDouble) + 2 * scaling * (0/50.toDouble) + 3 * scaling * (0/50.toDouble) + 4 * scaling * (0/50.toDouble) + powers.slice(4, 10).map(_ * scaling).sum) +- eps)

    var totalEnergy = graph.energy + graph.callees("a").head.energy + graph.callees("a").head.callees("b").head.energy
    totalEnergy += graph.callees("a").head.callees("c").head.energy + graph.callees("b").head.energy + graph.callees("b").head.callees("c").head.energy

    graph.totalEnergy should equal (totalEnergy +- eps)
    graph.callees("a").head.totalEnergy should equal (totalEnergy +- eps)
    graph.callees("b").head.totalEnergy should equal (totalEnergy +- eps)
    graph.callees("a").head.callees("b").head.totalEnergy should equal (totalEnergy +- eps)
    graph.callees("a").head.callees("c").head.totalEnergy should equal (totalEnergy +- eps)
    graph.callees("b").head.callees("c").head.totalEnergy should equal (totalEnergy +- eps)

    graph.nbOfCalls should equal(1)
    graph.callees("a").head.nbOfCalls should equal(1)
    graph.callees("b").head.nbOfCalls should equal(1)
    graph.callees("a").head.callees("b").head.nbOfCalls should equal(1)
    graph.callees("a").head.callees("c").head.nbOfCalls should equal(1)
    graph.callees("b").head.callees("c").head.nbOfCalls should equal(1)
  }

  it should "allow to add a Callee from a node and to merge callees if needed" in {
    val startTime = System.nanoTime

    val frequency = 50l.millis
    val scaling = frequency.toUnit(SECONDS)
    val powers = (1 to 50).map(_.toDouble).toList

    val rawGraph = Node("main", frequency, powers)
    rawGraph.parent = None
    rawGraph.rawStartTime = startTime
    rawGraph.rawStopTime = startTime + 1650l.millis.toNanos
    rawGraph.executionIntervals = List((rawGraph.rawStartTime, rawGraph.rawStopTime))

    val node1 = Node("a", frequency, powers)
    val node2 = Node("a", frequency, powers)
    node1.parent = Some(rawGraph)
    node1.rawStartTime = startTime + 100l.millis.toNanos
    node1.rawStopTime = startTime + 300l.millis.toNanos
    node2.parent = Some(rawGraph)
    node2.rawStartTime = startTime + 350l.millis.toNanos
    node2.rawStopTime = startTime + 1600l.millis.toNanos
    val subNode1_1 = Node("b", frequency, powers)
    val subNode1_2 = Node("c", frequency, powers)
    subNode1_1.parent = Some(node1)
    subNode1_1.rawStartTime = startTime + 110l.millis.toNanos
    subNode1_1.rawStopTime = startTime + 250l.millis.toNanos
    subNode1_2.parent = Some(node1)
    subNode1_2.rawStartTime = startTime + 260l.millis.toNanos
    subNode1_2.rawStopTime = startTime + 270l.millis.toNanos
    val subNode2_1 = Node("c", frequency, powers)
    subNode2_1.parent = Some(node2)
    subNode2_1.rawStartTime = startTime + 370l.millis.toNanos
    subNode2_1.rawStopTime = startTime + 410l.millis.toNanos
    val subNode2_2 = Node("c", frequency, powers)
    subNode2_2.parent = Some(subNode2_1)
    subNode2_2.rawStartTime = startTime + 385l.millis.toNanos
    subNode2_2.rawStopTime = startTime + 400l.millis.toNanos
    val node3 = Node("d", frequency, powers)
    node3.parent = Some(node2)
    node3.rawStartTime = startTime + 450l.millis.toNanos
    node3.rawStopTime = startTime + 1500l.millis.toNanos
    val subNode3_1 = Node("e", frequency, powers)
    subNode3_1.parent = Some(node3)
    subNode3_1.rawStartTime = startTime + 460l.millis.toNanos
    subNode3_1.rawStopTime = startTime + 750l.millis.toNanos

    node1.addCallee(subNode1_1)
    node1.addCallee(subNode1_2)
    rawGraph.addCallee(node1)
    node3.addCallee(subNode3_1)
    subNode2_1.addCallee(subNode2_2)
    node2.addCallee(subNode2_1)
    node2.addCallee(node3)
    rawGraph.addCallee(node2)

    val graph = GraphUtil.buildCallgraph(rawGraph)

    graph.callees.keys should contain only("a")
    graph.callees("a").head.callees.keys should contain only("b", "c", "d")
    graph.callees("a").head.callees("c").head.callees.keys should contain only("c")
    graph.callees("a").head.callees("d").head.callees.keys should contain only("e")

    graph.effectiveDuration(startTime, startTime + 10000l.millis.toNanos) should
      (be >= 0l.nanos and equal((1650 - ((300 - 100) + (1600 - 350))).toLong.millis))
    graph.callees("a").head.effectiveDuration(startTime, startTime + 10000l.millis.toNanos) should
      (be >= 0l.nanos and equal((((300 - 100) + (1600 - 350)) - ((250 - 110) + ((270 - 260) + (410 - 370)) + (1500 - 450))).toLong.millis))
    graph.callees("a").head.callees("b").head.effectiveDuration(startTime, startTime + 10000l.millis.toNanos) should
      (be >= 0l.nanos and equal((250 - 110).toLong.millis))
    graph.callees("a").head.callees("c").head.effectiveDuration(startTime, startTime + 10000l.millis.toNanos) should
      (be >= 0l.nanos and equal((((270 - 260) + (410 - 370)) - (400 - 385)).toLong.millis))
    graph.callees("a").head.callees("c").head.callees("c").head.effectiveDuration(startTime, startTime + 10000l.millis.toNanos) should
      (be >= 0l.nanos and equal((400 - 385).toLong.millis))
    graph.callees("a").head.callees("d").head.effectiveDuration(startTime, startTime + 10000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((1500 - 450) - (750 - 460)).toLong.millis))
    graph.callees("a").head.callees("d").head.callees("e").head.effectiveDuration(startTime, startTime + 10000l.millis.toNanos) should
      (be >= 0l.nanos and equal((750 - 460).toLong.millis))

    graph.duration should
      (be >= 0l.nanos and equal((50 + 50 + 4 * 0 + 50 + 25 * 0 + 50).toLong.millis))
    graph.callees("a").head.duration should
      (be >= 0l.nanos and equal((2 * 0 + 10 + 2 * 0 + 40 + 0 + 20 + 40 + 21 * 0 + 50 + 50).toLong.millis))
    graph.callees("a").head.callees("b").head.duration should
      (be >= 0l.nanos and equal((2 * 0 + 40 + 50 + 50).toLong.millis))
    graph.callees("a").head.callees("c").head.duration should
      (be >= 0l.nanos and equal((5 * 0 + 10 + 0 + 15 + 10).toLong.millis))
    graph.callees("a").head.callees("c").head.callees("c").head.duration should
      (be >= 0l.nanos and equal((7 * 0 + 15).toLong.millis))
    graph.callees("a").head.callees("d").head.duration should
      (be >= 0l.nanos and equal((9 * 0 + 10 + 5 * 0 + 15 * 50).toLong.millis))
    graph.callees("a").head.callees("d").head.callees("e").head.duration should
      (be >= 0l.nanos and equal((9 * 0 + 40 + 50 * 5).toLong.millis))

    graph.energy should
      equal((1 * scaling * (50/50.toDouble) + 2 * scaling * (50/50.toDouble) + powers.slice(6, 7).map(_ * scaling).sum + powers.slice(32, 33).map(_ * scaling).sum) +- eps)
    graph.callees("a").head.energy should
      equal((powers.slice(2, 3).map(_ * scaling).sum * (10/50.toDouble) + powers.slice(5, 6).map(_ * scaling).sum * (40/50.toDouble)
      + powers.slice(7, 8).map(_ * scaling).sum * (20/50.toDouble) + powers.slice(8, 9).map(_ * scaling).sum * (40/50.toDouble) + powers.slice(30, 32).map(_ * scaling).sum) +- eps)
    graph.callees("a").head.callees("b").head.energy should
      equal((powers.slice(2, 3).map(_ * scaling).sum * (40/50.toDouble) + powers.slice(3, 5).map(_ * scaling).sum) +- eps)
    graph.callees("a").head.callees("c").head.energy should
      equal((powers.slice(5, 6).map(_ * scaling).sum * (10/50.toDouble) + powers.slice(7, 8).map(_ * scaling).sum * (15/50.toDouble) + powers.slice(8, 9).map(_ * scaling).sum * (10/50.toDouble)) +- eps)
    graph.callees("a").head.callees("c").head.callees("c").head.energy should
      equal((powers.slice(7, 8).map(_ * scaling).sum * (15/50.toDouble)) +- eps)
    graph.callees("a").head.callees("d").head.energy should
      equal((powers.slice(9, 10).map(_ * scaling).sum * (10/50.toDouble) + powers.slice(15, 30).map(_ * scaling).sum) +- eps)
    graph.callees("a").head.callees("d").head.callees("e").head.energy should
      equal((powers.slice(9, 10).map(_ * scaling).sum * (40/50.toDouble) + powers.slice(10, 15).map(_ * scaling).sum) +- eps)

    graph.nbOfCalls should equal(1)
    graph.callees("a").head.nbOfCalls should equal(2)
    graph.callees("a").head.callees("b").head.nbOfCalls should equal(1)
    graph.callees("a").head.callees("c").head.nbOfCalls should equal(2)
    graph.callees("a").head.callees("c").head.callees("c").head.nbOfCalls should equal(1)
    graph.callees("a").head.callees("d").head.nbOfCalls should equal(1)
    graph.callees("a").head.callees("d").head.callees("e").head.nbOfCalls should equal(1)
  }
}
