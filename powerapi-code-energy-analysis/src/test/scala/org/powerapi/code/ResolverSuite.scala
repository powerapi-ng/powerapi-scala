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
package org.powerapi.code

import java.util.UUID

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.pattern.ask
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{Thread, TimeInStates, GlobalCpuTime, OSHelper}
import org.powerapi.core.target.{TargetUsageRatio, Process, Application}
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ResolverSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("ResolverSuite"))

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "An AddressResolver actor" should "create routees for resolving hexadecimal addresses to function names" in {
    val osHelper = new OSHelper {
      def getThreads(process: Process): Set[Thread] = ???
      def getTimeInStates: TimeInStates = ???
      def getGlobalCpuPercent(muid: UUID): TargetUsageRatio = ???
      def getFunctionNameByAddress(binaryPath: String, address: String): Option[String] = address match {
        case "0x1" => Some("a")
        case "0x2" => Some("b")
        case _ => Some("unknown")
      }
      def getCPUFrequencies: Set[Long] = ???
      def getProcessCpuPercent(muid: UUID, process: Process): TargetUsageRatio = ???
      def getProcessCpuTime(process: Process): Option[Long] = ???
      def getGlobalCpuTime: GlobalCpuTime = ???
      def getProcesses(application: Application): Set[Process] = ???

    }
    val router = TestActorRef(Props(classOf[AddressResolver], osHelper, timeout, 2))(system)

    Await.result(router.ask(ConvertAddress("/bin/test", "0x1"))(timeout), timeout.duration) should equal(Some("a"))
    Await.result(router.ask(ConvertAddress("/bin/test", "0x2"))(timeout), timeout.duration) should equal(Some("b"))

    Await.result(router.ask(ConvertAddress("/bin/test", "0x1"))(timeout), timeout.duration) should equal(Some("a"))
    Await.result(router.ask(ConvertAddress("/bin/test", "0x2"))(timeout), timeout.duration) should equal(Some("b"))
    Await.result(router.ask(ConvertAddress("/bin/test", "0x3"))(timeout), timeout.duration) should equal(Some("unknown"))
  }
}
