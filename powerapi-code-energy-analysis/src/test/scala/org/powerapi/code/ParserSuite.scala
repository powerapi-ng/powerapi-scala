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

import java.util.UUID
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{GlobalCpuTime, TimeInStates, Thread, OSHelper}
import org.powerapi.core.target.{Application, TargetUsageRatio, Process}
import scala.concurrent.duration.DurationLong
import scala.concurrent.Await
import scala.io.Source

class ParserSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("ParserSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val timeout = Timeout(10l.second)

  val basepath = getClass.getResource("/").getPath

  "A Parser actor" should "create routees for correctly parsing files to call graphs" in {
    val startTime = 1444051953026000000l

    val frequency = 250l.millis
    val powers = (1 to 15).map(_.toDouble).toList

    val osHelper = new OSHelper {
      def getThreads(process: Process): Set[Thread] = ???
      def getTimeInStates: TimeInStates = ???
      def getGlobalCpuPercent(muid: UUID): TargetUsageRatio = ???
      def getFunctionNameByAddress(binaryPath: String, address: String): Option[String] = address match {
        case "0x1"|"0x2" => Some("main")
        case "0x3"|"0x4" => Some("h")
        case "0x5" => Some("i")
        case "0x6" => Some("j")
        case "0x7"|"0x8" => Some("k")
        case "0x9" => Some("a")
        case "0x10"|"0x11" => Some("b")
        case "0x12" => Some("c")
        case "0xx" => Some("thread")
        case _ => None
      }
      def getCPUFrequencies: Set[Long] = ???
      def getProcessCpuPercent(muid: UUID, process: Process): TargetUsageRatio = ???
      def getProcessCpuTime(process: Process): Option[Long] = ???
      def getGlobalCpuTime: GlobalCpuTime = ???
      def getProcesses(application: Application): Set[Process] = ???
    }
    val resolverRouter = TestActorRef(Props(classOf[AddressResolver], osHelper, timeout, 8))(system)
    val parserRouter = TestActorRef(Props(classOf[Parser], timeout, "/bin/test", 1, resolverRouter))(system)

    val traces = Source.fromFile(s"${basepath}code-energy-trace.txt").getLines().toIterable
    val graphs = Await.result(parserRouter.ask(StartParsing(traces, frequency, powers))(timeout), timeout.duration).asInstanceOf[Seq[Node]]

    val mergedRawGraph = graphs.head

    for(rawGraph <- graphs.tail) {
      mergedRawGraph.mergeRawGraph(rawGraph)
    }

    val graph = GraphUtil.buildCallgraph(mergedRawGraph)

    graph.callees.keys should contain only("thread", "a", "i", "h")
    graph.callees("h").head.callees.keys should be ('empty)
    graph.callees("a").head.callees.keys should contain only("b")
    graph.callees("a").head.callees("b").head.callees.keys should contain only("c")
    graph.callees("a").head.callees("b").head.callees("c").head.callees.keys should contain only("c")
    graph.callees("i").head.callees.keys should contain only("j")
    graph.callees("i").head.callees("j").head.callees.keys should contain only("k")
    graph.callees("i").head.callees("j").head.callees("k").head.callees.keys should contain only("k")
    graph.callees("thread").head.callees.keys should contain only("h", "a")
    graph.callees("thread").head.callees("h").head.callees.keys should be('empty)
    graph.callees("thread").head.callees("a").head.callees.keys should contain only("b")
    graph.callees("thread").head.callees("a").head.callees("b").head.callees.keys should contain only("c")
    graph.callees("thread").head.callees("a").head.callees("b").head.callees("c").head.callees.keys should contain only("c")

    graph.fullName should equal("main")
    graph.callees("h").head.fullName should equal("main.h")
    graph.callees("a").head.fullName should equal("main.a")
    graph.callees("i").head.fullName should equal("main.i")
    graph.callees("a").head.callees("b").head.fullName should equal("main.a.b")
    graph.callees("a").head.callees("b").head.callees("c").head.fullName should equal("main.a.b.c")
    graph.callees("a").head.callees("b").head.callees("c").head.callees("c").head.fullName should equal("main.a.b.c.c")
    graph.callees("i").head.callees("j").head.fullName should equal("main.i.j")
    graph.callees("i").head.callees("j").head.callees("k").head.fullName should equal("main.i.j.k")
    graph.callees("i").head.callees("j").head.callees("k").head.callees("k").head.fullName should equal("main.i.j.k.k")
    graph.callees("thread").head.callees("h").head.fullName should equal("main.thread.h")
    graph.callees("thread").head.callees("a").head.callees("b").head.fullName should equal("main.thread.a.b")
    graph.callees("thread").head.callees("a").head.callees("b").head.callees("c").head.fullName should equal("main.thread.a.b.c")
    graph.callees("thread").head.callees("a").head.callees("b").head.callees("c").head.callees("c").head.fullName should equal("main.thread.a.b.c.c")

    graph.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal((15000 - ((6000 - 100) + (10000 - 6100))).toLong.millis))
    graph.callees("h").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal((2100 - 100).toLong.millis))
    graph.callees("a").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((10000 - 6100) - (6400 - 6150)).toLong.millis))
    graph.callees("a").head.callees("b").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((6400 - 6150) - (6395 - 6200)).toLong.millis))
    graph.callees("a").head.callees("b").head.callees("c").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((6395 - 6200) - (6380 - 6250)).toLong.millis))
    graph.callees("a").head.callees("b").head.callees("c").head.callees("c").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal((6380 - 6250).toLong.millis))
    graph.callees("i").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((6000 - 2300) - (5990 - 2310)).toLong.millis))
    graph.callees("i").head.callees("j").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((5990 - 2310) - (5900 - 2330)).toLong.millis))
    graph.callees("i").head.callees("j").head.callees("k").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((5900 - 2330) - (5800 - 3340)).toLong.millis))
    graph.callees("i").head.callees("j").head.callees("k").head.callees("k").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal((5800 - 3340).toLong.millis))
    graph.callees("thread").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((5950 - 105) - ((5950 - 2105) + (2050 - 105))).toLong.millis))
    graph.callees("thread").head.callees("h").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal((2050 - 105).toLong.millis))
    graph.callees("thread").head.callees("a").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((5950 - 2105) - (2350 - 2155)).toLong.millis))
    graph.callees("thread").head.callees("a").head.callees("b").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((2350 - 2155) - (2345 - 2205)).toLong.millis))
    graph.callees("thread").head.callees("a").head.callees("b").head.callees("c").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal(((2345 - 2205) - (2330 - 2255)).toLong.millis))
    graph.callees("thread").head.callees("a").head.callees("b").head.callees("c").head.callees("c").head.effectiveDuration(startTime, startTime + 15000l.millis.toNanos) should
      (be >= 0l.nanos and equal((2330 - 2255).toLong.millis))
  }
}
