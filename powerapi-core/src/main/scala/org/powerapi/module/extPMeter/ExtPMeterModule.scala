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
package org.powerapi.module.extPMeter

import org.powerapi.PowerModule
import org.powerapi.core.{ExternalPMeter, OSHelper}
import org.powerapi.core.power.Power
import scala.concurrent.duration.FiniteDuration

class ExtPMeterModule(osHelper: OSHelper, extPMeter: ExternalPMeter, idlePower: Power) extends PowerModule {
  lazy val underlyingClasses = eventBus match {
    case Some(bus) => {
      (Seq((classOf[ExtPMeterSensor], Seq(extPMeter))), Seq((classOf[ExtPMeterFormula], Seq(osHelper, idlePower))))
    }
    case _ => (Seq(), Seq())
  }

  lazy val underlyingSensorsClasses = underlyingClasses._1
  lazy val underlyingFormulaeClasses = underlyingClasses._2
}

