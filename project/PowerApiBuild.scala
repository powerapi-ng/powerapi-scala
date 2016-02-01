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

  lazy val rootSettings = Seq(
    version := "3.4",
    scalaVersion := "2.11.7",
    scalacOptions := Seq(
      "-language:reflectiveCalls",
      "-language:implicitConversions",
      "-feature",
      "-deprecation"
    ),
    fork := true,
    parallelExecution := false,
    unmanagedBase := baseDirectory.value / "external-libs"
  )

  lazy val subSettings = rootSettings ++ Seq(
    unmanagedBase :=  baseDirectory.value / ".." / "external-libs"
  )

  lazy val powerapi = Project(id = "powerapi", base = file(".")).settings(rootSettings: _*).aggregate(powerapiCore, powerapiCli, powerapiDaemon, powerapiSampling)

  lazy val powerapiCore = Project(id = "powerapi-core", base = file("powerapi-core")).settings(subSettings: _*)
  lazy val powerapiCli = Project(id = "powerapi-cli", base = file("powerapi-cli")).settings(subSettings: _*).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(BluecovePackaging, JavaAppPackaging)
  lazy val powerapiDaemon = Project(id = "powerapi-daemon", base = file("powerapi-daemon")).settings(subSettings: _*).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(BluecovePackaging, JavaServerAppPackaging)
  lazy val powerapiSampling = Project(id = "powerapi-sampling", base = file("powerapi-sampling")).settings(subSettings: _*).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(BluecovePackaging, JavaAppPackaging)
}
