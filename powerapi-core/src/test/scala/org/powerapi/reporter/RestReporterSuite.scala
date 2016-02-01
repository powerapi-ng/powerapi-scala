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
package org.powerapi.reporter

import scala.concurrent.duration.{FiniteDuration, Duration, DurationInt}

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import akka.util.Timeout

import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.testkit.ScalatestRouteTest
import StatusCodes._

import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers, WordSpec}

import org.powerapi.{PowerMeter, UnitTest}
import org.powerapi.module.cpu.simple.ProcFSCpuSimpleModule

class RestReporterMock(pm: PowerMeter, system: ActorSystem) extends RestReporter(pm, system) {
  override def preStart() {
  }
}

class RestReporterSuite extends WordSpec with Matchers with ScalatestRouteTest with RestService {
  def actorRefFactory = system
  import RestServiceJsonProtocol._
  
  val cpu_simple = new PowerMeter(actorRefFactory, Seq(ProcFSCpuSimpleModule()))
  val restReporter = actorRefFactory.actorOf(Props(classOf[RestReporterMock], cpu_simple, actorRefFactory))

  val configuration = new RestReporterConfiguration {}
  configuration.hostAddress should equal("127.0.0.1")
  configuration.dbAddress should equal("127.0.0.1")
  
  def reporter = restReporter
  
  "The RestService" should {

    "return a empty PIDs list for GET requests to /energy in begining" in {
      Get("/energy") ~> rest ~> check { responseAs[TargetList].list should have size 0 }
    }
    
    "return a '1234 started' response for POST requests to /energy/1234/start" in {
      Post("/energy/1234/start") ~> rest ~> check { responseAs[String] should equal ("1234 started") }
      Thread.sleep((1.second).toMillis)
    }
    
    "return a non-empty PIDs list for GET requests to /energy after starting the monitoring of one process" in {
      Get("/energy") ~> rest ~> check { responseAs[TargetList].list should contain ("1234") }
      Thread.sleep((1.second).toMillis)
    }
    
    "return a '5678 started' response for POST requests to /energy/5678/start" in {
      Post("/energy/5678/start") ~> rest ~> check { responseAs[String] should equal ("5678 started") }
    }
    
    "return a non-empty PIDs list for GET requests to /energy after starting the monitoring of two processes" in {
      Get("/energy") ~> rest ~> check {
        responseAs[TargetList].list should contain ("1234")
        responseAs[TargetList].list should contain ("5678")
      }
    }
    
    "return a '1234 stopped' response for POST requests to /energy/1234/stop" in {
      Post("/energy/1234/stop") ~> rest ~> check { responseAs[String] should equal ("1234 stopped") }
      Thread.sleep((1.second).toMillis)
    }
    
    "return a '5678 stopped' response for POST requests to /energy/5678/stop" in {
      Post("/energy/5678/stop") ~> rest ~> check { responseAs[String] should equal ("5678 stopped") }
      Thread.sleep((1.second).toMillis)
    }

    "return a empty PIDs list for GET requests to /energy after stopping the monitoring of the process" in {
      Get("/energy") ~> rest ~> check { responseAs[TargetList].list should have size 0 }
    }

    "leave GET requests to other paths unhandled" in {
      Get("/kermit") ~> rest ~> check { handled should equal (false) }
    }
    
    "leave POST requests to other paths unhandled" in {
      Post("/kermit") ~> rest ~> check { handled should equal (false) }
    }

    "return a MethodNotAllowed error for POST requests to /energy" in {
      Post("/energy") ~> sealRoute(rest) ~> check {
        status should equal (MethodNotAllowed)
        responseAs[String] should equal ("HTTP method not allowed, supported methods: GET")
      }
    }

    "return a MethodNotAllowed error for PUT requests to /energy" in {
      Put("/energy") ~> sealRoute(rest) ~> check {
        status should equal (MethodNotAllowed)
        responseAs[String] should equal ("HTTP method not allowed, supported methods: GET")
      }
    }
    
    "return a MethodNotAllowed error for DELETE requests to /energy" in {
      Delete("/energy") ~> sealRoute(rest) ~> check {
        status should equal (MethodNotAllowed)
        responseAs[String] should equal ("HTTP method not allowed, supported methods: GET")
      }
    }
  }
}

