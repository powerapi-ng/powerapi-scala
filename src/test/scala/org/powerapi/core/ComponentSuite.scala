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

import java.util.UUID

import akka.actor.SupervisorStrategy.{Directive, Escalate, Restart, Resume, Stop}
import akka.actor.{ActorRef, ActorSystem, Props, Terminated}
import akka.event.LoggingReceive
import akka.testkit.{EventFilter, TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.MonitorTarget

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class TestComponent extends Component {
  def receive = LoggingReceive {
    case "msg" => sender ! "ok"
  } orElse default
}

class TestSupervisor(f: PartialFunction[Throwable, Directive]) extends Component with Supervisor {
  def handleFailure: PartialFunction[Throwable, Directive] = f

  def receive = {
    case p: Props => sender ! context.actorOf(p)
    case ex: Exception => throw ex
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

case class MessageWrapperMock(muid: UUID, target: Target, frequency: FiniteDuration, timestamp: Long)

class SensorMock(eventBus: MessageBus, actorRef: ActorRef) extends Sensor(eventBus) {
  def process(monitorTarget: MonitorTarget): Unit = {
    actorRef ! MessageWrapperMock(monitorTarget.muid, monitorTarget.target, monitorTarget.frequency, monitorTarget.timestamp)
  }
}

class ComponentSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("ComponentSuite", ConfigFactory.parseResources("test.conf")))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A component" should "have a default behavior and a processing one" in {
    val component = TestActorRef(Props(classOf[TestComponent]))(system)
    component ! "msg"
    expectMsg("ok")
    intercept[UnsupportedOperationException] { component.receive(new Exception("oups")) }
  }

  it can "handle failures if needed" in {
    def handleFailureOne: PartialFunction[Throwable, Directive] = {
      case _: ArithmeticException => Resume
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: Exception => Escalate
    }

    val supervisor = TestActorRef(Props(classOf[TestSupervisor], handleFailureOne))(system)
    supervisor ! Props[TestChild]
    var child = expectMsgClass(classOf[ActorRef])

    child ! 42
    child ! "state"
    expectMsg(42)

    EventFilter.warning(occurrences = 1, source = child.path.toString).intercept({
      child ! new ArithmeticException("bad operation")
      child ! "state"
      expectMsg(42)
    })(system)

    EventFilter[NullPointerException](occurrences = 1, source = child.path.toString).intercept({
      child ! new NullPointerException("null !")
      child ! "state"
      expectMsg(0)
    })(system)

    EventFilter[IllegalArgumentException](occurrences = 1, source = child.path.toString).intercept({      
      watch(child)
      child ! new IllegalArgumentException("bad argument")
      expectMsgPF() { case Terminated(child) => () }
    })(system)

    EventFilter[Exception]("crash", occurrences = 1, source = supervisor.path.toString).intercept({
      supervisor ! Props[TestChild]
      child = expectMsgClass(classOf[ActorRef])
      watch(child)
      child ! 42
      child ! "state"
      expectMsg(42)
      child ! new Exception("crash")
      expectMsgPF() { case t @ Terminated(child) if t.existenceConfirmed => () }
    })(system)
  }

  "A Sensor" should "process MonitorTarget messages" in new Bus {
    import org.powerapi.core.MonitorChannel.publishTarget

    val sensorMock = TestActorRef(Props(classOf[SensorMock], eventBus, testActor))(system)

    val muid = UUID.randomUUID()
    val frequency = 25.milliseconds
    val target = Process(0)
    val timestamp = 1l

    publishTarget(muid, target, frequency, timestamp)(eventBus)
    expectMsg(MessageWrapperMock(muid, target, frequency, timestamp))
  }

  "A different failure strategy" can "be applied for different supervisors" in {
    def handleFailureOne: PartialFunction[Throwable, Directive] = {
      case _: ArithmeticException => Restart
    }

    val supervisor = TestActorRef(Props(classOf[TestSupervisor], handleFailureOne))(system)
    supervisor ! Props[TestChild]
    val child = expectMsgClass(classOf[ActorRef])

    child ! 42
    child ! "state"
    expectMsg(42)
    
    EventFilter[ArithmeticException](occurrences = 1, source = child.path.toString).intercept({
      child ! new ArithmeticException("bad operation")
      child ! "state"
      expectMsg(0)
    })(system)
  }

  "Our default guardian strategy" should "be applied for the supervisor actor" in {
    val supervisor = TestActorRef(Props(classOf[TestSupervisor], null))(system)

    EventFilter.warning(occurrences = 1, source = supervisor.path.toString).intercept({
      supervisor ! new UnsupportedOperationException("umh, not supported")
    })(system)

    EventFilter[Exception]("crash", occurrences = 1, source = supervisor.path.toString).intercept({
      supervisor ! new Exception("crash")
    })(system)
  }
}
