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

import java.util.UUID

import org.powerapi.PowerModule
import org.powerapi.core.MessageBus
import org.powerapi.core.target.Target
import org.powerapi.module.hwc.{LikwidHelper, RAPLDomain}

class RAPLCpuSensor(eventBus: MessageBus, muid: UUID, target: Target, likwidHelper: LikwidHelper) extends RAPLSensor(eventBus, muid, target, likwidHelper, RAPLDomain.PKG)

class RAPLDramSensor(eventBus: MessageBus, muid: UUID, target: Target, likwidHelper: LikwidHelper) extends RAPLSensor(eventBus, muid, target, likwidHelper, RAPLDomain.DRAM)

class RAPLCpuFormula(eventBus: MessageBus, muid: UUID, target: Target) extends RAPLFormula(eventBus, muid, target, RAPLDomain.PKG)

class RAPLDramFormula(eventBus: MessageBus, muid: UUID, target: Target) extends RAPLFormula(eventBus, muid, target, RAPLDomain.DRAM)

class RAPLCpuModule(likwidHelper: LikwidHelper) extends PowerModule {
  val sensor = Some((classOf[RAPLCpuSensor], Seq[Any](likwidHelper)))
  val formula = Some((classOf[RAPLCpuFormula], Seq[Any]()))
}

class RAPLDramModule(likwidHelper: LikwidHelper) extends PowerModule {
  val sensor = Some((classOf[RAPLDramSensor], Seq[Any](likwidHelper)))
  val formula = Some((classOf[RAPLDramFormula], Seq[Any]()))
}

object RAPLCpuModule {
  def apply(likwidHelper: LikwidHelper): RAPLCpuModule = {
    new RAPLCpuModule(likwidHelper)
  }
}

object RAPLDramModule {
  def apply(likwidHelper: LikwidHelper): RAPLDramModule = {
    new RAPLDramModule(likwidHelper)
  }
}
