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
package org.powerapi.module.rapl

import org.powerapi.PowerModule
import org.powerapi.module.hwc.{LikwidHelper, RAPLDomain}
import org.powerapi.module.hwc.RAPLDomain.RAPLDomain

class RAPLModule(likwidHelper: LikwidHelper, domain: RAPLDomain) extends PowerModule {
  val sensor = Some((classOf[RAPLSensor], Seq[Any](likwidHelper, domain)))
  val formula = Some((classOf[RAPLFormula], Seq[Any](domain)))
}

object RaplCpuModule {
  def apply(likwidHelper: LikwidHelper): RAPLModule = {
    new RAPLModule(likwidHelper, RAPLDomain.PKG)
  }
}

object RaplDramModule {
  def apply(likwidHelper: LikwidHelper): RAPLModule = {
    new RAPLModule(likwidHelper, RAPLDomain.DRAM)
  }
}
