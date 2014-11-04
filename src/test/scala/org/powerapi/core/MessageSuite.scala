/**
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 * 
 * Copyright (C) 2011-2014 Inria, University of Lille 1.
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

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */

package org.powerapi.core

import org.powerapi.UnitTesting

import akka.actor.ActorSystem
import akka.testkit._
import org.scalatest._

case class MessageReport(suid: Long, topic: String) extends Report

class MessageSuite(_system: ActorSystem) extends UnitTesting(_system) {
  import ReportBus.eventBus

  def this() = this(ActorSystem("MessageSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "The ReportBus" should "handle messages by topic" in {
    val report = MessageReport(1, "topic1")
    val report2 = MessageReport(1, "topic2")

    eventBus.subscribe(testActor, "topic1")
    eventBus.publish(report)
    eventBus.publish(report2)
    expectMsg(report)
  }
}