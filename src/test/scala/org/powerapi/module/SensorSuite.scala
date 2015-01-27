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
package org.powerapi.module

import java.util.UUID
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import scala.concurrent.duration.DurationInt

class SensorMock(eventBus: MessageBus, actorRef: ActorRef) extends SensorComponent(eventBus) {
  import org.powerapi.core.MonitorChannel.MonitorTick
  import SensorChannel.{MonitorStop, MonitorStopAll}

  def sense(monitorTick: MonitorTick): Unit = {
    actorRef ! monitorTick
  }

  def monitorStopped(msg: MonitorStop): Unit = {
    actorRef ! msg
  }

  def monitorAllStopped(msg: MonitorStopAll): Unit = {
    actorRef ! msg
  }
}
class SensorSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("SensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A Sensor" should "process MonitorTick messages" in new Bus {
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MonitorChannel.{MonitorTick, publishMonitorTick}
    import org.powerapi.core.target.{intToProcess, Target}
    import SensorChannel.{MonitorStop, MonitorStopAll, monitorStopped, monitorAllStopped}

    val sensorMock = TestActorRef(Props(classOf[SensorMock], eventBus, testActor))(system)

    val muid = UUID.randomUUID()
    val target: Target = 1
    val clockTick = ClockTick("test", 25.milliseconds)

    publishMonitorTick(muid, target, clockTick)(eventBus)
    monitorStopped(muid)(eventBus)
    monitorAllStopped()(eventBus)

    expectMsgClass(classOf[MonitorTick]) match {
      case MonitorTick(_, id, targ, tick) => id should equal(muid); targ should equal(target); tick should equal(clockTick)
    }

    expectMsgClass(classOf[MonitorStop]) match {
      case MonitorStop(_, id) => id should equal(muid)
    }

    expectMsgClass(classOf[MonitorStopAll])
  }
}
