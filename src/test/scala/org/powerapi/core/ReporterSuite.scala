/*
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
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.core

import java.util.UUID
import akka.actor.{Actor, ActorNotFound, ActorRef, ActorSystem, Props}
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.powerapi.UnitTest
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

import org.powerapi.reporter.AggPowerChannel.AggPowerReport
import org.powerapi.reporter.ReporterComponent

class ReporterComponentMock(actorRef: ActorRef) extends ReporterComponent {
  def report(aggPowerReport: AggPowerReport): Unit = {
    actorRef ! aggPowerReport
  }
}

class ReporterSuite(system: ActorSystem) extends UnitTest(system) {
  import ClockChannel.ClockTick
  import ReporterChannel.{ ReporterStart, ReporterStop, formatReporterChildName, startReporter, stopReporter }
  import org.powerapi.module.PowerChannel.{ PowerReport, publishPowerReport }
  import org.powerapi.module.PowerUnit
  import org.powerapi.reporter.AggPowerChannel.subscribeAggPowerReport

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("ReporterSuite"))

  val eventListener = ConfigFactory.parseResources("test.conf")

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "The Reporter actors" should "launch an exception when the messages received cannot be handled" in new Bus {
    val _system = ActorSystem("ReporterSuiteTest1", eventListener)

    val muid = UUID.randomUUID()
    val nbTargets = 3
    val aggFunction = (l: List[PowerReport]) => l.foldLeft(0.0){ (acc, r) => acc + r.power }
    
    val reporter = _system.actorOf(Props(classOf[ReporterChild], eventBus, muid, nbTargets, aggFunction), "reporter1")
    val reporters = _system.actorOf(Props(classOf[Reporters], eventBus), "reporters1")

    EventFilter.warning(occurrences = 1, source = reporter.path.toString).intercept({
      reporter ! ReporterStop("test", muid)
    })(_system)

    EventFilter.warning(occurrences = 1, source = reporter.path.toString).intercept({
      reporter ! ReporterStart("test", UUID.randomUUID(), nbTargets, aggFunction)
    })(_system)

    // Not an exception, just an assessment (switching in the running state).
    EventFilter.info(occurrences = 1, source = reporter.path.toString).intercept({
      reporter ! ReporterStart("test", muid, nbTargets, aggFunction)
    })(_system)

    EventFilter.warning(occurrences = 1, source = reporter.path.toString).intercept({
      reporter ! ReporterStart("test", muid, nbTargets, aggFunction)
    })(_system)

    EventFilter.warning(occurrences = 1, source = reporter.path.toString).intercept({
      reporter ! ReporterStop("test", UUID.randomUUID())
    })(_system)

    EventFilter.warning(occurrences = 1, source = reporters.path.toString).intercept({
      stopReporter(muid)(eventBus)
    })(_system)

    Await.result(gracefulStop(reporter, timeout.duration), timeout.duration)
    Await.result(gracefulStop(reporters, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A ReporterChild actor" should "start to listen power reports for its monitor and produce aggregated messages" in new Bus {
    val _system = ActorSystem("ReporterSuiteTest2", eventListener)

    val muid = UUID.randomUUID()
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
    val nbTargets = 3
    val aggFunction = (l: List[PowerReport]) => l.foldLeft(0.0){ (acc, r) => acc + r.power }
    
    val reporter = _system.actorOf(Props(classOf[ReporterChild], eventBus, muid, nbTargets, aggFunction), "reporter2")
    val watcher = TestProbe()(_system)
    watcher.watch(reporter)
    subscribeAggPowerReport(muid)(eventBus)(testActor)
    
    EventFilter.info(occurrences = 1, source = reporter.path.toString).intercept({
      reporter ! ReporterStart("test", muid, nbTargets, aggFunction)
    })(_system)
    
    publishPowerReport(muid, Process(1), 4.0, PowerUnit.W, device, tickMock)(eventBus)
    publishPowerReport(muid, Process(2), 5.0, PowerUnit.W, device, tickMock)(eventBus)
    publishPowerReport(muid, Process(3), 6.0, PowerUnit.W, device, tickMock)(eventBus)

    EventFilter.info(occurrences = 1, source = reporter.path.toString).intercept({
      reporter ! ReporterStop("test", muid)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(reporter)
    }, 20.seconds)
    
    expectMsgClass(classOf[AggPowerReport]).power should equal(15.0)
    
    Await.result(gracefulStop(reporter, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it can "handle a large number of power reports" in new Bus {
    import java.lang.Thread
  
    val _system = ActorSystem("ReporterSuiteTest3")

    val muid = UUID.randomUUID()
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
    val nbTargets = 50
    val aggFunction = (l: List[PowerReport]) => l.foldLeft(0.0){ (acc, r) => acc + r.power }

    val reporter = _system.actorOf(Props(classOf[ReporterChild], eventBus, muid, nbTargets, aggFunction), "reporter3")
    val watcher = TestProbe()(_system)
    watcher.watch(reporter)
    subscribeAggPowerReport(muid)(eventBus)(testActor)

    reporter ! ReporterStart("test", muid, nbTargets, aggFunction)
    
    for(i <- 1 to 150) {
      publishPowerReport(muid, Process(i), i*3.0, PowerUnit.W, device, tickMock)(eventBus)
    }
    
    Thread.sleep(1000)
    
    reporter ! ReporterStop("test", muid)

    awaitAssert({
      watcher.expectTerminated(reporter)
    }, 20.seconds)
    
    expectMsgClass(10.minute, classOf[AggPowerReport]).power should equal(3825.0)
    expectMsgClass(10.minute, classOf[AggPowerReport]).power should equal(11325.0)
    expectMsgClass(10.minute, classOf[AggPowerReport]).power should equal(18825.0)

    Await.result(gracefulStop(reporter, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A Reporters actor" should "handle its ReporterChild actors and the reporter component have to receive messages from attached monitors" in new Bus {
    import java.lang.Thread
    
    val _system = ActorSystem("ReporterSuiteTest4")

    val reporters = _system.actorOf(Props(classOf[Reporters], eventBus), "reporters4")

    val targets = List(Process(1))
    val power = 1.0
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
    val aggFunction = (l: List[PowerReport]) => l.foldLeft(0.0){ (acc, r) => acc + r.power }
    val reporter = new Reporter(eventBus, _system, aggFunction, classOf[ReporterComponentMock], List(testActor))

    val attachedMonitors = scala.collection.mutable.ListBuffer[Monitor]()

    for(i <- 0 until 100) {
      val monitor = new Monitor(eventBus, targets)
      attachedMonitors += monitor
      reporter.attach(monitor)
    }
    
    Thread.sleep(1000)
    
    for(i <- 0 until 100) {
      publishPowerReport(attachedMonitors(i).muid, targets(0), power, PowerUnit.W, device, tickMock)(eventBus)
    }
    
    Thread.sleep(1000)
    
    receiveN(100, 10.minute)
    
    Thread.sleep(1000)
    
    for(i <- 0 until 100) {
      reporter.detach(attachedMonitors(i))
    }
    
    reporter.cancel()
    Await.result(gracefulStop(reporters, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it should "handle a large number of reporters and reporter components" in new Bus {
    import java.lang.Thread
    
    val _system = ActorSystem("ReporterSuiteTest5")

    val reporters = _system.actorOf(Props(classOf[Reporters], eventBus), "reporters5")

    val targets = List(Process(1))
    val power = 1.0
    val device = "mock"
    val tickMock = ClockTick("ticktest", 25.milliseconds)
    val aggFunction = (l: List[PowerReport]) => l.foldLeft(0.0){ (acc, r) => acc + r.power }

    val attachedMonitors = scala.collection.mutable.ListBuffer[(Monitor, Reporter)]()

    for(i <- 0 until 100) {
      val monitor = new Monitor(eventBus, targets)
      val reporter = new Reporter(eventBus, _system, aggFunction, classOf[ReporterComponentMock], List(testActor))
      attachedMonitors += ((monitor, reporter))
      reporter.attach(monitor)
    }
    
    Thread.sleep(1000)
    
    for(i <- 0 until 100) {
      publishPowerReport(attachedMonitors(i)._1.muid, targets(0), power, PowerUnit.W, device, tickMock)(eventBus)
    }
    
    Thread.sleep(1000)
    
    receiveN(100, 10.minute)
    
    Thread.sleep(1000)
    
    for(i <- 0 until 100) {
      attachedMonitors(i)._2.detach(attachedMonitors(i)._1)
      attachedMonitors(i)._2.cancel()
    }
    
    Await.result(gracefulStop(reporters, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }
}

