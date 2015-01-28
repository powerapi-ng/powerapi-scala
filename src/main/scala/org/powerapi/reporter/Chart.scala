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
package org.powerapi.reporter

import java.awt.BasicStroke
import java.awt.Dimension

import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.data.time.FixedMillisecond
import org.jfree.data.time.TimeSeries
import org.jfree.data.time.TimeSeriesCollection
import org.jfree.data.time.TimeSeriesDataItem
import org.jfree.ui.ApplicationFrame

/**
 * Display result received from the CpuDiskListener Component to a wrapped JFreeChart chart.
 *
 * @author Aurélien Bourdon <aurelien.bourdon@gmail.com>
 */
class Chart(title: String) {
  val dataset = new TimeSeriesCollection
  val chart = ChartFactory.createTimeSeriesChart(title,
    Chart.xValues, Chart.yValues, dataset, true, true, false)
  val timeSeries = collection.mutable.HashMap[String, TimeSeries]()

  def process(values: Map[String, Double], timestamp: Long) {
    values.foreach({ value =>
      if (!timeSeries.contains(value._1)) {
        val serie = new TimeSeries(value._1)
        dataset.addSeries(serie)
        timeSeries += (value._1 -> serie)
        chart.getXYPlot().getRenderer().setSeriesStroke(dataset.getSeriesCount() - 1, new BasicStroke(3))
      }
      timeSeries(value._1).addOrUpdate(new TimeSeriesDataItem(new FixedMillisecond(timestamp), value._2))
    })
  }
}

/**
 * Chart companion object containing the JFreeChart's ApplicationFrame and some configurations.
 *
 * @author abourdon
 */
object Chart {
  val xValues = "Time (s)"
  val yValues = "Power (W)"
  val title = "PowerAPI"
  lazy val chart = {
    val ch = new Chart(title)
    val plot = ch.chart.getXYPlot()
    plot.setBackgroundPaint(java.awt.Color.WHITE)
    plot.setDomainGridlinesVisible(true)
    plot.setDomainGridlinePaint(java.awt.Color.GRAY)
    plot.setRangeGridlinesVisible(true)
    plot.setRangeGridlinePaint(java.awt.Color.GRAY)
    ch
  }

  val chartPanel = {
    val panel = new ChartPanel(chart.chart)
    panel.setMouseWheelEnabled(true)
    panel.setDomainZoomable(true)
    panel.setFillZoomRectangle(true)
    panel.setRangeZoomable(true)
    panel
  }

  val applicationFrame = {
    val app = new ApplicationFrame(title)
    app
  }

  def run() {
    applicationFrame.setContentPane(chartPanel)
    applicationFrame.setSize(new Dimension(800, 600))
    applicationFrame.setVisible(true)
  }

  def process(values: Map[String, Double], timestamp: Long) {
    chart.process(values, timestamp)
  }
}

