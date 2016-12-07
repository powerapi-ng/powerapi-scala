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
package org.powerapi.module.libpfm

import java.util.UUID

import scala.collection.BitSet
import scala.concurrent.duration.DurationInt

import akka.actor.Props
import akka.testkit.TestActorRef
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, LibpfmPickerStop}
import org.scalamock.scalatest.MockFactory

class LibpfmPickerSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A LibpfmPicker" should "collect the performance counter values" in new Bus {
    val configuration = BitSet()
    val helper = mock[LibpfmHelper]
    val muid = UUID.randomUUID()
    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    val tick3 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 2000
    }
    val tick4 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 3000
    }

    helper.resetPC _ expects * once() returning true
    helper.enablePC _ expects * once() returning true
    helper.disablePC _ expects * once() returning true
    helper.closePC _ expects * once() returning true
    helper.configurePC _ expects(CID(0), configuration, "event") returning Some(0)
    helper.readPC _ expects 0 returning Array(1, 1, 1)

    val picker = TestActorRef(Props(classOf[LibpfmPicker], helper, "event", 0, None, configuration), testActor, "picker")(system)

    helper.readPC _ expects 0 returning Array(1, 1, 1)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(1l, 1l, 1l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning None
    picker ! tick1
    var pcReport = expectMsgClass(classOf[HWCounter])
    pcReport.value should equal(0l)

    helper.readPC _ expects 0 returning Array(2, 2, 2)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(2l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(1)
    picker ! tick2
    pcReport = expectMsgClass(classOf[HWCounter])
    pcReport.value should equal(1)

    helper.readPC _ expects 0 returning Array(30, 4, 4)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(30l, 4l, 4l).deep && old.deep == Array(2l, 2l, 2l).deep
    } returning Some(28)
    picker ! tick3
    pcReport = expectMsgClass(classOf[HWCounter])
    pcReport.value should equal(28l)

    helper.readPC _ expects 0 returning Array(30, 4, 4)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(30l, 4l, 4l).deep && old.deep == Array(30l, 4l, 4l).deep
    } returning None
    picker ! tick4
    pcReport = expectMsgClass(classOf[HWCounter])
    pcReport.value should equal(0)

    picker ! LibpfmPickerStop

    system.stop(picker)
  }
}
