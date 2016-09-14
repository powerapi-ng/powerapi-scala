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

import com.typesafe.sbt.packager.archetypes.{JavaServerAppPackaging, JavaAppPackaging}
import sbt.Keys._
import sbt._

object PowerApiBuild extends Build {

  lazy val downloadBluecove = taskKey[File]("download-bluecove-app")

  lazy val downloadBluecoveGpl = taskKey[File]("download-bluecove-gpl-app")

  lazy val buildSettings = Seq(
    version := "4.1",
    scalaVersion := "2.11.7",
    scalacOptions := Seq(
      "-language:existentials",
      "-language:reflectiveCalls",
      "-language:implicitConversions",
      "-feature",
      "-deprecation",
      "-unchecked",
      "-Xfatal-warnings"
    ),
    fork := true,
    parallelExecution := false,
    unmanagedBase := powerapi.base.getAbsoluteFile / "external-libs",
    unmanagedClasspath in Test += powerapi.base.getAbsoluteFile  / "external-libs" / "sigar-bin",
    unmanagedClasspath in (Compile, runMain) += powerapi.base.getAbsoluteFile  / "external-libs" / "sigar-bin",
    downloadBluecove := {
      val locationBluecove = powerapi.base.getAbsoluteFile / "external-libs" / "bluecove-2.1.0.jar"
      if (!locationBluecove.exists()) IO.download(url("https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/bluecove/bluecove-2.1.0.jar"), locationBluecove)
      locationBluecove
    },
    downloadBluecoveGpl := {
      val locationBluecoveGpl = powerapi.base.getAbsoluteFile / "external-libs" / "bluecove-gpl-2.1.0.jar"
      if (!locationBluecoveGpl.exists()) IO.download(url("https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/bluecove/bluecove-gpl-2.1.0.jar"), locationBluecoveGpl)
      locationBluecoveGpl
    },
    compile in Compile <<= (compile in Compile).dependsOn(downloadBluecove, downloadBluecoveGpl)
  )

  lazy val powerapi: sbt.Project = Project(id = "powerapi", base = file(".")).settings(buildSettings: _*).aggregate(powerapiCore, powerapiCli, powerapiDaemon, powerapiSampling)

  lazy val mwgPlugin = Project(id="mwg-plugin",base = file("mwg-plugin")).settings(buildSettings: _*)
  lazy val mwgServer = Project(id="mwg-server",base = file("mwg-server")).settings(buildSettings: _*)

  lazy val powerapiCore = Project(id = "powerapi-core", base = file("powerapi-core")).settings(buildSettings: _*).dependsOn(mwgPlugin % "compile -> compile")
  lazy val powerapiCli = Project(id = "powerapi-cli", base = file("powerapi-cli")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)
  lazy val powerapiDaemon = Project(id = "powerapi-daemon", base = file("powerapi-daemon")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(JavaServerAppPackaging)
  lazy val powerapiSampling = Project(id = "powerapi-sampling", base = file("powerapi-sampling")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)
  lazy val powerapiCodeEnergyAnalysis = Project(id = "powerapi-code-energy-analysis", base = file("powerapi-code-energy-analysis")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)

  // example of power meters
  lazy val appMonitorProcsJava =  Project(id = "powerapi-example-app-monitor-procfs-java", base = file("powerapi-powermeter/AppMonitorProcFSJava")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile")
  lazy val appMonitorProcsScala =  Project(id = "powerapi-example-app-monitor-procfs-scala", base = file("powerapi-powermeter/AppMonitorProcFSScala")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile")
  lazy val appMonitorSigarJava =  Project(id = "powerapi-example-app-monitor-sigar-java", base = file("powerapi-powermeter/AppMonitorSigarJava")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile")
  lazy val appMonitorSigarScala =  Project(id = "powerapi-example-app-monitor-sigar-scala", base = file("powerapi-powermeter/AppMonitorSigarScala")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile")
  lazy val cpuMonitorOmegaWatt =  Project(id = "powerapi-example-cpu-monitor-omegawatt", base = file("powerapi-powermeter/CPUMonitorOmegaWatt")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile")
  lazy val cpuMonitorProcsFS =  Project(id = "powerapi-example-cpu-monitor-procfs", base = file("powerapi-powermeter/CPUMonitorProcFS")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile")
  lazy val cpuMonitorRAPL =  Project(id = "powerapi-example-cpu-monitor-rapl", base = file("powerapi-powermeter/CPUMonitorRAPL")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile")
  lazy val cpuMonitorSigar =  Project(id = "powerapi-example-cpu-monitor-sigar", base = file("powerapi-powermeter/CPUMonitorSigar")).settings(buildSettings: _*).dependsOn(powerapiCore % "compile -> compile")
}
