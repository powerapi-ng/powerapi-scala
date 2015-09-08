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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.powerapi.UnitTest
import scala.collection.BitSet
import scala.sys.process.stringSeqToProcess

class LibpfmHelperSuite(system: ActorSystem) extends UnitTest(system) {
  def this() = this(ActorSystem("LibpfmHelperSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val helper = new LibpfmHelper() {
    override lazy val nrPerfEventOpen = 298 // Linux Intel/AMD 64 bits.
  }

  "An implicit method" should "convert a BitSet to a long" in {
    var bitset = BitSet()
    var long: Long = bitset
    long should equal(0L)
    bitset = BitSet(0, 1)
    // Only 23 bits are allowed
    bitset += 24
    long = bitset
    long should equal((1L << 0) + (1L << 1))
    bitset = BitSet()
    bitset += 0
    bitset += 1
    bitset += 2
    long = bitset
    long should equal((1L << 0) + (1L << 1) + (2L << 1))
  }

  "The LibpfmHelper" should "read correctly the configuration value from a file" in {
    val helper = new LibpfmHelper()
    helper.nrPerfEventOpen should equal(128)
  }

  "The scale method" should "scale correctly the values passed as arguments" in {
    var now = Array[Long](10, 2, 2)
    var old = Array[Long](1, 1, 1)
    helper.scale(now, old) should equal(Some(9))

    now = Array[Long](10, 0, 0)
    helper.scale(now, old) should equal(None)

    now = Array[Long](10, 2, 3)
    helper.scale(now, old) should equal(None)

    now = Array[Long](10, 2, 2)
    old = Array[Long](1, 2, 2)
    helper.scale(now, old) should equal(None)
  }

  "The detectedEvents method" should "detect the events available on the CPU for an UNIX system" ignore {
    helper.init()
    helper.availablePMUS().isEmpty should equal(false)
    helper.deinit()
  }

  "The libpfm library" can "be used on linux" ignore {
    val basepath = getClass.getResource("/").getPath

    val pid = Seq("bash", s"${basepath}test-pc.bash").lineStream(0).trim.toInt
    val configuration = BitSet(0, 1)

    helper.init() should equal(true)
    helper.configurePC(pid, -1, configuration, "cycles") match {
      case Some(fd) => {
        helper.resetPC(fd) should equal(true)
        helper.enablePC(fd) should equal(true)
        Seq("kill", "-SIGCONT", s"$pid").!

        for(_ <- 0 to 5) {
          val values = helper.readPC(fd)
          println(s"value: ${values(0)}, enabled time: ${values(1)}, running time: ${values(2)}")
          Thread.sleep(500)
        }

        Seq("kill", "-SIGKILL", s"$pid").!

        helper.disablePC(fd) should equal(true)
        helper.closePC(fd) should equal(true)
        helper.deinit()
      }
      case None => assert(false)
    }
  }
}
