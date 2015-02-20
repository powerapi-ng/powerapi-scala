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
package org.powerapi.sampling

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.powerapi.UnitTest

class PolynomialRegressionSuite(system: ActorSystem) extends UnitTest(system) {
  import akka.util.Timeout
  import scala.concurrent.duration.DurationDouble

  def this() = this(ActorSystem("PolynomialRegressionSuite"))

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val basepath = getClass.getResource("/").getPath

  trait Formulae {
    var formulae = List[String]()
    formulae :+= "powerapi.libpfm.formulae.cycles = ["
    formulae :+= "  { coefficient = 12.0, formula = [85.55811224463605,1.066313337003709E-8,-1.7928640462696824E-18] }"
    formulae :+= "  { coefficient = 13.0, formula = [88.28269217397201,7.95691655787089E-9,-1.040258455087571E-18] }"
    formulae :+= "  { coefficient = 14.0, formula = [86.45427081763455,1.0055171130458987E-8,-1.4230332213523363E-18] }"
    formulae :+= "  { coefficient = 15.0, formula = [88.31259983074074,8.430845614885964E-9,-1.0241853007611094E-18] }"
    formulae :+= "  { coefficient = 16.0, formula = [85.2842389844891,1.0852927133195294E-8,-1.3435195930394241E-18] }"
    formulae :+= "  { coefficient = 17.0, formula = [86.07805423676109,1.025221739299229E-8,-1.2284301797275693E-18] }"
    formulae :+= "  { coefficient = 18.0, formula = [85.78480212254863,1.0512362042003832E-8,-1.1234369080415169E-18] }"
    formulae :+= "  { coefficient = 19.0, formula = [87.43918320780358,9.883391772784685E-9,-9.490433245731516E-19] }"
    formulae :+= "  { coefficient = 20.0, formula = [86.76834374302143,1.0775634401278774E-8,-1.0735020780617666E-18] }"
    formulae :+= "  { coefficient = 21.0, formula = [99.34501719557329,4.421825525123567E-9,4.7687130916337836E-20] }"
    formulae :+= "  { coefficient = 22.0, formula = [98.61733097609249,8.643025647889484E-9,-6.103536352575776E-19] }"
    formulae :+= "]"
  }

  "The PolynomialRegression object" should "process the data files from a directory and write the formulae inside a result file" in new Formulae {
    import scalax.file.Path

    PolynomialRegression(s"${basepath}processing", "/tmp/formulae").run()
    (Path("/") / ("/tmp/formulae/libpfm-formula.conf", '/')).lines().toList should contain theSameElementsAs formulae
  }
}
