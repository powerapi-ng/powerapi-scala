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
package org.powerapi.reporter

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{FiniteDuration, Duration, DurationInt}
import scalax.io.Resource
import scalax.file.Path

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import akka.util.Timeout

import org.powerapi.{PowerMeter, UnitTest}
import org.powerapi.module.cpu.simple.ProcFSCpuSimpleModule


class FuseReporterSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("FuseReporterSuite"))
  
  val eventListener = ConfigFactory.parseResources("test.conf")

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A FUSE reporter" should "display a list of monitored processes in a virtual file system" ignore {
    val _system = ActorSystem("FuseReporterSuiteTest1", eventListener)
  
    val cpu_simple = new PowerMeter(_system, Seq(ProcFSCpuSimpleModule()))
    val fuse = _system.actorOf(Props(classOf[FuseReporter], cpu_simple))

    val configuration = new FuseReporterConfiguration {}
    configuration.fuseFileName should equal("./PowerAPI")
    
    val powerAPIFile = configuration.fuseFileName

    //cpu_simple.waitFor(1.hour)
    Thread.sleep((1.second).toMillis)
      
    Resource.fromInputStream(Runtime.getRuntime.exec(Array("ls","-l",powerAPIFile+"/")).getInputStream).lines()
      .toList(1).split("\\s").last should equal ("energy")
    Resource.fromInputStream(Runtime.getRuntime.exec(Array("ls","-l",powerAPIFile+"/energy/")).getInputStream).lines()
      .toList should have size 1
    
    Runtime.getRuntime.exec(Array("mkdir",powerAPIFile+"/energy/1234"))
    
    //Thread.sleep((1.second).toMillis)
    
    Resource.fromInputStream(Runtime.getRuntime.exec(Array("ls","-l",powerAPIFile+"/energy")).getInputStream).lines()
      .toList.map(_.split("\\s").last) should contain ("1234")
    val pidDir = Resource.fromInputStream(Runtime.getRuntime.exec(Array("ls","-l",powerAPIFile+"/energy/1234")).getInputStream).lines()
      .toList.map(_.split("\\s").last)
    pidDir should have size 4
    pidDir should contain ("frequency")
    pidDir should contain ("energy")
    pidDir should contain ("power")
    
    Resource.fromInputStream(Runtime.getRuntime.exec(Array("cat",powerAPIFile+"/energy/1234/frequency")).getInputStream).lines()
      .toList(0).size should be > 0
      Resource.fromInputStream(Runtime.getRuntime.exec(Array("cat",powerAPIFile+"/energy/1234/energy")).getInputStream).lines()
      .toList(0).size should be > 0
      Resource.fromInputStream(Runtime.getRuntime.exec(Array("cat",powerAPIFile+"/energy/1234/power")).getInputStream).lines()
      .toList(0).size should be > 0
      
    Runtime.getRuntime.exec(Array("rmdir",powerAPIFile+"/energy/1234"))
    
    Thread.sleep((1.second).toMillis)
    
    Resource.fromInputStream(Runtime.getRuntime.exec(Array("ls","-l",powerAPIFile+"/energy/")).getInputStream).lines()
      .toList should have size 1
      
    Runtime.getRuntime.exec(Array("mkdir",powerAPIFile+"/energy/firefox"))
    
    Resource.fromInputStream(Runtime.getRuntime.exec(Array("cat",powerAPIFile+"/energy/firefox/power")).getInputStream).lines()
      .toList(0).size should be > 0
      
    Runtime.getRuntime.exec(Array("rmdir",powerAPIFile+"/energy/firefox"))
    
    Thread.sleep((1.second).toMillis)
    
    Runtime.getRuntime.exec(Array("mkdir",powerAPIFile+"/energy/123,456"))
    
    Resource.fromInputStream(Runtime.getRuntime.exec(Array("cat",powerAPIFile+"/energy/123,456/power")).getInputStream).lines()
      .toList(0).size should be > 0
      
    Runtime.getRuntime.exec(Array("rmdir",powerAPIFile+"/energy/123,456"))
    
    Thread.sleep((1.second).toMillis)
    
    cpu_simple.shutdown
    fuse ! StopFuse
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }
}

