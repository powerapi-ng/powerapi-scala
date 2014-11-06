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

import org.powerapi.test.UnitTest

import akka.actor.{ ActorRef, ActorSystem, Props, Terminated }
import akka.actor.SupervisorStrategy.{ Directive, Escalate, Restart, Resume, Stop }
import akka.event.LoggingReceive

import akka.testkit.{ TestActorRef, TestKit }

class TestComponent extends Component {
  def receive = LoggingReceive {
    case "msg" => sender ! "ok"
  } orElse default
}

class TestSupervisor extends Component with Supervisor {
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: ArithmeticException => Resume
    case _: NullPointerException => Restart
    case _: IllegalArgumentException => Stop
    case _: Exception => Escalate
  }

  def receive = {
    case p: Props => sender ! context.actorOf(p)
  }
}

class TestChild extends Component {
  var state = 0

  def receive = {
    case ex: Exception => throw ex
    case x: Int => state = x
    case "state" => sender ! state
  }
}

class ComponentSuite(_system: ActorSystem) extends UnitTest(_system) {
  def this() = this(ActorSystem("ComponentSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A component" should "have a default behavior and a processing one" in {
    val component = TestActorRef[TestComponent]
    component ! "msg"
    expectMsg("ok")
    intercept[UnsupportedOperationException] { component.receive(new Exception("oups")) }
  }

  it can "handle failures if needed" in {
    val supervisor = TestActorRef[TestSupervisor]
    supervisor ! Props[TestChild]
    var child = expectMsgClass(classOf[ActorRef])
    
    child ! 42
    child ! "state"
    expectMsg(42)

    child ! new ArithmeticException("bad operation")
    child ! "state"
    expectMsg(42)

    child ! new NullPointerException("null !")
    child ! "state"
    expectMsg(0)

    watch(child)
    child ! new IllegalArgumentException("bad argument")
    expectMsgPF() { case Terminated(child) => () }

    supervisor ! Props[TestChild]
    child = expectMsgClass(classOf[ActorRef])
    watch(child)
    child ! 42
    child ! "state"
    expectMsg(42)
    child ! new Exception("crash")
    expectMsgPF() { case t @ Terminated(child) if t.existenceConfirmed => () }
  }
}
