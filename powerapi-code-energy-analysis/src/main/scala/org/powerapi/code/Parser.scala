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

import akka.actor.{ActorRef, Props, Actor}
import akka.routing.RoundRobinPool
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

case class StartParsing(traces: Iterable[String], monitoringFrequency: FiniteDuration, powers: List[Double])

object ParserUtils {
  def groupByTid(traces: Iterable[String]): Seq[Iterable[String]] = {
    val tids = traces.iterator.foldLeft(Set[Long]())((acc, line) => acc + line.split(" ").apply(0).toLong)

    (for(tid <- tids) yield traces.iterator.filter(_.split(" ").apply(0).toLong == tid).toIterable).toSeq
  }
}

/**
 * This actor acts as a router and it allows to convert a trace file (which contains a call graph) to a call graph in memory.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class Parser(timeout: Timeout, binaryFilePath: String, nbRoutees: Int, addressResolver: ActorRef) extends Actor {
  val router = context.actorOf(RoundRobinPool(nbRoutees).props(Props(classOf[ParserRoutee], timeout, binaryFilePath, addressResolver)))

  override def postStop(): Unit = {
    context.stop(router)
  }

  def receive: Actor.Receive = {
    case StartParsing(traces, monitoringFrequency, powers) => {
      val graphs = Future.sequence(for(tracesPerTid <- ParserUtils.groupByTid(traces)) yield {
        val msg = StartParsing(tracesPerTid, monitoringFrequency, powers)
        router.ask(msg)(timeout).asInstanceOf[Future[Node]]
      })

      pipe(graphs) to sender
    }
  }
}

class ParserRoutee(timeout: Timeout, binaryFilePath: String, addressResolver: ActorRef) extends Actor {
  def receive = {
    case StartParsing(tracesIterable, monitoringFrequency, powers) => {
      val traces = tracesIterable.iterator
      val firstLineFields = traces.next().split(" ")

      val graph = Node(Await.result(addressResolver.ask(ConvertAddress(binaryFilePath, firstLineFields(2)))(timeout).asInstanceOf[Future[Option[String]]], timeout.duration).get, monitoringFrequency, powers)
      graph.rawStartTime = firstLineFields(4).toLong

      var current = graph

      for (line <- traces) {
        val fields = line.split(" ")

        fields(1).toLowerCase match {
          case "e" => {
            val nodeName = Await.result(addressResolver.ask(ConvertAddress(binaryFilePath, fields(2)))(timeout).asInstanceOf[Future[Option[String]]], timeout.duration)
            val node = Node(nodeName.get, monitoringFrequency, powers)
            node.parent = Some(current)
            node.rawStartTime = fields(4).toLong

            current.addCallee(node)
            current = node
          }
          case "x" => {
            current.rawStopTime = fields(4).toLong

            if(current.parent.isDefined) {
              current = current.parent.get
            }

            else graph.executionIntervals = List((graph.rawStartTime, graph.rawStopTime))
          }
        }
      }

      sender ! graph
    }
  }
}
