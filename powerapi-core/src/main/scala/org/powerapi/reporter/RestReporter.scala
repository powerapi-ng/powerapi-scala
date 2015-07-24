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
package org.powerapi.reporter

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.{ DurationLong, FiniteDuration }

import akka.actor.{ Actor, ActorSystem, ActorLogging, ActorRef, Props }
import akka.event.LoggingReceive
import akka.io.IO
import akka.pattern.ask

import spray.can.Http 
import spray.client.pipelining._
import spray.http.{ HttpHeaders, MediaTypes }
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling._
import spray.json.DefaultJsonProtocol
import spray.routing._

import com.github.nscala_time.time.Imports.DateTime

import org.powerapi.PowerMeter
import org.powerapi.core.{APIComponent, ConfigValue, Configuration}
import org.powerapi.core.target._
import org.powerapi.module.PowerChannel.AggregatePowerReport


case class Data(host: String, targets: String, energy: Double)
case class Event(`type`: String, time: String, data: Data)
case class TargetList(list: List[String])

object RestServiceJsonProtocol extends DefaultJsonProtocol {
  implicit val dataFormat = jsonFormat3(Data)
  implicit val eventFormat = jsonFormat3(Event)
  implicit val targetListFormat = jsonFormat1(TargetList)
}


/**
 * Main Configuration
 */
class RestReporterConfiguration extends Configuration(None) {
  /** Host IP address used for HTTP server and for identifying a PowerAPI instance on distributed context. */
  lazy val hostAddress: String = load { _.getString("powerapi.rest.host-address") } match {
    case ConfigValue(value) => value
    case _ => "localhost"
  }
  /** Database IP address. */
  lazy val dbAddress: String = load { _.getString("powerapi.rest.database-address") } match {
    case ConfigValue(value) => value
    case _ => "localhost"
  }
}

case class StartProcessMonitoring(targets: Set[String])
case class StopProcessMonitoring(targets: Set[String])

/**
 * Dedicated type of message to get each monitored processes.
 */
object GetTargetList

/**
 * Display power information via a REST interface
 *
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class RestReporter(pm: PowerMeter, system: ActorSystem) extends RestReporterConfiguration with APIComponent {
  def actorRefFactory = context
  implicit def executionContext = actorRefFactory.dispatcher
  import RestServiceJsonProtocol._
  
  // monitored processes.
  // The value is compute from aggregate power reports.
  // [process -> muid]
  lazy val targetInfo = collection.mutable.HashMap[Set[Target], Option[UUID]]()
  
  override def preStart() {
    context.actorOf(Props(classOf[RestActor], self, system))
  }
  
  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: AggregatePowerReport => report(msg)
    case msg: StartProcessMonitoring => start(msg)
    case msg: StopProcessMonitoring => stop(msg)
    case GetTargetList => getTargetList
  } orElse default
  
  def report(aggPowerReport: AggregatePowerReport): Unit = {
    if (targetInfo contains aggPowerReport.targets)
      targetInfo(aggPowerReport.targets) = Some(aggPowerReport.muid)
    val req = Post("http://"+dbAddress+":1080/1.0/event/put",
                   List[Event](Event("request",
                                     DateTime.now.toString,
                                     Data(hostAddress,
                                          aggPowerReport.targets.mkString(","),
                                          aggPowerReport.power.toWatts))))
    val pipeline = sendReceive
    pipeline(req)
  }
  
  def start(msg: StartProcessMonitoring): Unit = {
    val targets: Set[Target] = for (target <- msg.targets) yield {
      if (target forall Character.isDigit) Process(target.toInt) else Application(target)
    }
    if (!targetInfo.contains(targets))
      pm.monitor(1.second)(targets.toSeq:_*) to self
  }
  
  def stop(msg: StopProcessMonitoring): Unit = {
    val targets: Set[Target] = for (target <- msg.targets) yield {
      if (target forall Character.isDigit) Process(target.toInt) else Application(target)
    }
    if (targetInfo contains targets)
      targetInfo(targets) match {
        case Some(muid) => pm.stopMonitor(muid)
        case None => log.warning("target(s) {} doesn't exists", targets)
      }
  }
  
  def getTargetList: Unit = {
    val currentProcesses = pm.getMonitoredProcesses.toSet
    val oldProcesses = targetInfo.keySet -- currentProcesses
    targetInfo --= oldProcesses
    val newProcesses = currentProcesses -- targetInfo.keySet
    newProcesses.foreach(targets =>
      targetInfo += (targets -> targetInfo.getOrElse(targets, None))
    )
    sender ! targetInfo.keySet.map(_.mkString(",")).toList
  }
}

/**
 * REST Service actor
 */
class RestActor(restReporter: ActorRef, system: ActorSystem) extends RestReporterConfiguration with Actor with ActorLogging with RestService {
  def actorRefFactory = context
  
  override def preStart() {
    // start HTTP server with rest service actor as a handler
    IO(Http)(system) ! Http.Bind(self, hostAddress, 8080)
  }
  
  def receive = LoggingReceive {
    runRoute(rest)
  }
  
  def reporter = restReporter
}

//REST Service
trait RestService extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher
  import RestServiceJsonProtocol._
  def reporter: ActorRef
  
  val rest = respondWithHeaders(HttpHeaders.`Access-Control-Allow-Origin`(spray.http.AllOrigins)) {
    path("energy") {
      get {
        respondWithMediaType(MediaTypes.`application/json`) {
          complete {
            TargetList(Await.result(reporter.ask(GetTargetList)(5.seconds), 5.seconds
                       ).asInstanceOf[List[String]])
          }
        }
      }
    } ~
    path("energy" / Segment / "start") {
      target =>
        post {
          complete {
            reporter ! StartProcessMonitoring(target.split(",").toSet)
            s"${target} started"
          }
        }
    } ~
    path("energy" / Segment / "stop") {
      target =>
        post {
          complete {
            reporter ! StopProcessMonitoring(target.split(",").toSet)
            s"${target} stopped"
          }
        }
    }
  }
}

