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
package org.powerapi.module.libpfm

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import java.util.UUID
import org.powerapi.core.MessageBus
import org.powerapi.UnitTest
import org.powerapi.core.target.All
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.MonitorChannel.MonitorTick
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll}
import org.scalamock.scalatest.MockFactory
import scala.collection.BitSet
import scala.concurrent.duration.DurationInt

class LibpfmPickerSuite(system: ActorSystem) extends UnitTest(system) with MockFactory {

  def this() = this(ActorSystem("LibpfmPickerSuite"))

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A DefaultLibpfmPicker" should "open, configure and collect values from a performance counter" in new Bus {
    val configuration = BitSet()
    val helper = mock[LibpfmHelper]
    val muid = UUID.randomUUID()

    val child = TestActorRef(Props(classOf[DefaultLibpfmPicker], helper, "event", -1, 0, configuration), testActor, "child1")(system)

    helper.resetPC _ expects * anyNumberOfTimes() returning true
    helper.enablePC _ expects * anyNumberOfTimes() returning true
    helper.disablePC _ expects * anyNumberOfTimes() returning true
    helper.closePC _ expects * anyNumberOfTimes() returning true

    helper.configurePC _ expects(-1, 0, configuration, "event", -1, 0l) returning Some(0)
    helper.readPC _ expects 0 repeat 2 returning Array(1, 1, 1)
    child ! MonitorTick("monitor", muid, All, ClockTick("clock", 500.milliseconds))
    expectMsgClass(classOf[Long]) should equal(1l)
    child ! MonitorTick("monitor", muid, All, ClockTick("clock", 500.milliseconds))
    expectMsgClass(classOf[Long]) should equal(0l)

    helper.readPC _ expects 0 returning Array(10, 2, 2)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(10l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(8)

    child ! MonitorTick("monitor", muid, All, ClockTick("clock", 500.milliseconds))
    expectMsgClass(classOf[Long]) should equal(8l)

    system.stop(child)
  }

  "A FDLibpfmPicker" should "collect values from a performance counter" in new Bus {
    val configuration = BitSet()
    val helper = mock[LibpfmHelper]
    val muid = UUID.randomUUID()

    val child = TestActorRef(Props(classOf[FDLibpfmPicker], helper, Some(3)), testActor, "child1")(system)

    helper.readPC _ expects 3 repeat 2 returning Array(1, 1, 1)
    child ! MonitorTick("monitor", muid, All, ClockTick("clock", 500.milliseconds))
    expectMsgClass(classOf[Long]) should equal(1l)
    child ! MonitorTick("monitor", muid, All, ClockTick("clock", 500.milliseconds))
    expectMsgClass(classOf[Long]) should equal(0l)

    helper.readPC _ expects 3 returning Array(10, 2, 2)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(10l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(8)

    child ! MonitorTick("monitor", muid, All, ClockTick("clock", 500.milliseconds))
    expectMsgClass(classOf[Long]) should equal(8l)

    system.stop(child)
  }

  "A LibpfmPicker" should "close correctly the resources" in {
    val configuration = BitSet()
    val helper = mock[LibpfmHelper]
    val reaper = TestProbe()(system)
    val muid1 = UUID.randomUUID()

    val child1 = TestActorRef(Props(classOf[DefaultLibpfmPicker], helper, "event", 1, 0, configuration), testActor, "child1")(system)
    val child2 = TestActorRef(Props(classOf[FDLibpfmPicker], helper, Some(4)), testActor, "child2")(system)
    reaper.watch(child1)
    reaper.watch(child2)

    helper.resetPC _ expects * anyNumberOfTimes() returning true
    helper.enablePC _ expects * anyNumberOfTimes() returning true
    helper.disablePC _ expects * anyNumberOfTimes() returning true
    helper.closePC _ expects * anyNumberOfTimes() returning true

    helper.configurePC _ expects(1, 0, configuration, "event", -1, 0l) returning Some(0)

    child1 ! MonitorStop("sensor", muid1)
    reaper.expectTerminated(child1, timeout.duration)

    child2 ! MonitorStopAll("sensor")
    reaper.expectTerminated(child2, timeout.duration)
  }
}
