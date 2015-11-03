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

import java.awt.{ BorderLayout, Dimension, FlowLayout, Frame, Toolkit }
import java.awt.event.{ ComponentAdapter, ComponentEvent }
import javax.swing.{ JButton, JFrame, JLabel, JPanel, JTextField }

import processing.core.{ PApplet, PFont, PImage, PVector }
import processing.core.PConstants.{ CENTER, OPENGL, P3D, QUADS, PI }
import processing.opengl.PGraphicsOpenGL
import shapes3d.{ Anchor, Box, Shape3D }
import peasy.PeasyCam

import org.powerapi.core.target.Process


/**
 * District represents a collection of building
 * 
 * @author lhuertas
 */
class District(val p: PApplet) extends Box(p, 0, 0, 0) {

  val buildings = new collection.mutable.LinkedHashMap[String, Building]()
                  with collection.mutable.SynchronizedMap[String, Building]
  private val move = 10f
  private var districWidth  = move
  private var districHeight = move
  
  
  this.visible(false, Box.ALL_SIDES)
  this.visible(true, Box.FRONT)
  
  this.fill(p.color(200, 200, 200))

  //---------------------------------------------------------------------------
  
  def addBuilding(pids: String, devices: String = "all") {
    val b = new Building(p, pids, devices)
    buildings += (pids -> b)
  }
  
  def removeBuilding(pids: String) {
    if (City.buildingFocused != null && City.buildingFocused.pids == pids) {
      City.buildingFocused = null
      //City.applicationFrame.refreshInfo
    }
    buildings -= pids
  }
  
  def refresh() {
    districWidth  = Math.ceil(Math.sqrt(buildings.size.toDouble)).toFloat * move
    districHeight = Math.round(Math.sqrt(buildings.size.toDouble)) * move
    
    coord(0).x  = -districWidth/2
    coord(1).x  = districWidth/2
    coord(2).x  = districWidth/2
    coord(3).x  = -districWidth/2
    coord(0).y  = -districHeight/2
    coord(1).y  = -districHeight/2
    coord(2).y  = districHeight/2
    coord(3).y  = districHeight/2
    
    var i,j = 0f
    buildings.values.foreach(b => {
      b.moveTo(i-(districWidth/2)+(move/2), j-(districHeight/2)+(move/2), 0)
      if (i == districWidth-move) {
        i = 0
        j += move
      }
      else {
        i += move
      }
    })
  }
  
  override def draw() {
    buildings.values.foreach(_.draw)
    super.draw
  }
}


/**
 * City represents a collection of district
 *
 * @author lhuertas
 */
class City extends PApplet {
  private var pcam:PeasyCam = _
  private val renderer = OPENGL
  private var isStarted = false
  private var base: District = _

  override def setup() {
    size(1280, 720, renderer)
    
    pcam = new PeasyCam(this, 150)
    pcam.reset
    
    mousePressed()
    
    base = new District(this)
    isStarted = true
  }
  
  override def draw() {
    background(255)
    lights()
    
    base.draw
  }
  
  override def mousePressed() {
    try {
      val shapehit = Shape3D.pickShape(this, mouseX, mouseY)
      if (shapehit.isInstanceOf[Building]) {
        if (City.buildingFocused != null) {
          City.buildingFocused.leftFocus
          City.buildingFocused = null
        }
          
        val buildingFocusing = shapehit.asInstanceOf[Building]
        if (base.buildings.contains(buildingFocusing.pids)) {
          buildingFocusing.getFocus
          City.buildingFocused = buildingFocusing
        }
        
      }
      keepPCamPosition
      City.applicationFrame.refreshInfo
    }
    catch {
      case e: Exception => {e.printStackTrace}
    }
  }
  
  //---
  
  def updateBuildings(monitoredProcesses: collection.mutable.Set[String]) {
    if (isStarted) {
      val currentProcesses = base.buildings.keySet
      val oldProcesses = currentProcesses -- monitoredProcesses
      val newProcesses = monitoredProcesses -- currentProcesses
      oldProcesses.foreach(process => base.removeBuilding(process))
      newProcesses.foreach(process => base.addBuilding(process))
      base.refresh
    }
  }
  
  def process(pids: String, power: Double, devices: String) {
    if (isStarted)
      if (!base.buildings.contains(pids)) {
        base.addBuilding(pids, devices)
        base.refresh
      }
      else
        base.buildings(pids).power = power.toFloat
  }
  
  def resetPCam() { if (isStarted) pcam.reset }
  def keepPCamPosition() { 
    if (isStarted) pcam.lookAt(pcam.getLookAt()(0),pcam.getLookAt()(1),pcam.getLookAt()(2))
  }
}

/**
 * Companion object of City class
 *
 * @author lhuertas
 */
object City {
  val title = "PowerAPI"
  val screenSize = Toolkit.getDefaultToolkit().getScreenSize()
  var isStarting = false
  private var _buildingFocused: Building = _
  
  lazy val city = {
    val c = new City
    applicationFrame.add(c, BorderLayout.CENTER)
    applicationFrame.getRootPane().addComponentListener(new ComponentAdapter() {
      override def componentResized(e: ComponentEvent) {
        c.keepPCamPosition
      }
    })
    isStarting = true
    c
  }
    
  val applicationFrame = new JFrame(title) {
    this.setLayout(new BorderLayout())
    
    val info = new JPanel()
    info.setLayout(new FlowLayout())
    
    	/*val field = new JTextField()
    	field.setColumns(10)
      info.add(field)

      val bouton = new JButton()
      info.add(bouton)*/

      val label = new JLabel("[Click on building]")
      info.add(label)
    
    this.add(info, BorderLayout.PAGE_END)
    
    new Thread("RefreshInfoThread") {
      override def run() {
        while(true) {
          Thread.sleep(1000)
          refreshInfo
        }
      }
    }.start()
    
    def refreshInfo() {
      if (buildingFocused != null)
        label.setText(buildingFocused.getInfo())
      else
        label.setText("[Click on building]")
    }
  }
  
  def run() {
    city.init
    Thread.sleep(2000)
    applicationFrame.setPreferredSize(new Dimension(1280, 740))
    applicationFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    applicationFrame.pack()
    applicationFrame.setVisible(true)
    //applicationFrame.setExtendedState(Frame.MAXIMIZED_BOTH)
  }
  
  def buildingFocused = this.synchronized { _buildingFocused }
  def buildingFocused_=(buildingFocused: Building) { this.synchronized { _buildingFocused =  buildingFocused } }
  
  def process(pids: String, power: Double, devices: String) {
    if (isStarting) city.process(pids, power, devices)
  }
}

