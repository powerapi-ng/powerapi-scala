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
package org.powerapi.reporter

import processing.core.{ PApplet, PImage, PVector }
import processing.core.PConstants.{ QUADS, PI }
import processing.opengl.PGraphicsOpenGL
import shapes3d.{ Box, Cone }
import shapes3d.SConstants.{ ALL, SOLID, WIRE }

/**
 * Building represents process with it power consumption as the height of building
 * 
 * @author lhuertas
 */
class Building(val p: PApplet, val pids: String, val devices: String) extends Box(p, 2.5f, 2.5f, 0.01f) {
  
  private var colorR: Float = 96
  private var colorG: Float = 96
  private var colorB: Float = 96
  
  private var _power = 0.01f
  def power = _power
  def power_=(power: Float) {
    if (power < 0.01f) {
      coord(0).z  = 0.01f
      coord(1).z  = 0.01f
      coord(2).z  = 0.01f
      coord(3).z  = 0.01f
    }
    else {
      coord(0).z  = power
      coord(1).z  = power
      coord(2).z  = power
      coord(3).z  = power
    }
    pointer.moveTo(0, 0, coord(0).z + 6)
    _power = power
  }
  
  val pointer = new Cone(p, 3)
  pointer.rotateToX(PI/2)
  pointer.scale(0.060f)
  pointer.fill(p.color(200, 32, 32), ALL)
  
  this.visible(false, Box.BACK)
  this.drawMode(SOLID | WIRE)
  
  this.fill(p.color(colorR, colorG, colorB))
  this.strokeWeight(0)
  
  //---------------------------------------------------------------------------
  
  def getInfo() = "Process(es) "+pids+" : power= "+_power//+" | devices= "+devices
  
  def getFocus() {
    this.addShape(pointer)
  }
  def leftFocus() {
    this.removeShape(pointer)
  }
  
  override def draw() {
    this.fill(p.color(colorR, colorG, colorB))
    super.draw
  }
}

