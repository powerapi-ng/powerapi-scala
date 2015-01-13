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
package org.powerapi.module.libpfm

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import org.powerapi.UnitTest

class LibpfmCoreSensorChildSuite(system: ActorSystem) extends UnitTest(system) {
  import akka.actor.Props
  import akka.util.Timeout
  import org.powerapi.core.MessageBus
  import scala.concurrent.duration.DurationDouble

  def this() = this(ActorSystem("LibpfmCoreSensorChildSuite"))

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A LibpfmCoreSensorChild" should "collect the performance counter values" ignore new Bus {
    import akka.testkit.TestProbe
    import java.util.{BitSet, UUID}
    import org.powerapi.core.All
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MonitorChannel.MonitorTick
    import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll}
    import scala.sys.process.stringSeqToProcess

    val basepath = getClass.getResource("/").getPath
    val pid = Seq("bash", s"${basepath}test-pc.bash").lineStream(0).trim.toInt
    Seq("taskset", "-cp", "0" ,s"$pid").!

    val configuration = new BitSet()
    configuration.set(0)
    configuration.set(1)
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    var msg = 0l

    LibpfmHelper.init()

    val reaper = TestProbe()(system)
    val child1 = TestActorRef(Props(classOf[LibpfmCoreSensorChild], "cycles", 0, None, configuration), testActor, "child1")(system)
    val child2 = TestActorRef(Props(classOf[LibpfmCoreSensorChild], "cycles", 0, None, configuration), testActor, "child2")(system)
    reaper.watch(child1)
    reaper.watch(child2)

    Seq("kill", "-SIGCONT", s"$pid").!!
    child1 ! MonitorTick("monitor", muid1, All, ClockTick("clock", 500.milliseconds))
    msg = expectMsgClass(classOf[Long])
    msg should be >= 0l
    println(s"muid: $muid1; event: cycles; value: $msg")
    child2 ! MonitorTick("monitor", muid2, All, ClockTick("clock", 500.milliseconds))
    msg = expectMsgClass(classOf[Long])
    msg should be >= 0l
    println(s"muid: $muid2; event: cycles; value: $msg")
    child1 ! MonitorTick("monitor", muid1, All, ClockTick("clock", 500.milliseconds))
    msg = expectMsgClass(classOf[Long])
    msg should be >= 0l
    println(s"muid: $muid1; event: cycles; value: $msg")
    child2 ! MonitorTick("monitor", muid2, All, ClockTick("clock", 500.milliseconds))
    msg = expectMsgClass(classOf[Long])
    msg should be >= 0l
    println(s"muid: $muid2; event: cycles; value: $msg")
    Seq("kill", "-SIGKILL", s"$pid").!!

    child1.underlyingActor.asInstanceOf[LibpfmCoreSensorChild].fd should not equal(None)
    child2.underlyingActor.asInstanceOf[LibpfmCoreSensorChild].fd should not equal(None)

    child1 ! MonitorStop("sensor", muid1)
    reaper.expectTerminated(child1, 1.seconds)
    child2 ! MonitorStopAll("sensor")
    reaper.expectTerminated(child2, 1.seconds)

    LibpfmHelper.deinit()
  }
}
