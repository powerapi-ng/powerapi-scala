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
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import sbt._
import scoverage.ScoverageSbtPlugin.ScoverageKeys

object PowerApiBuild extends Build {
  lazy val powerapi = Project(id = "powerapi", base = file(".")).aggregate(powerapiCore, powerapiCli, powerapiDaemon, powerapiSampling)

  lazy val powerapiCore = Project(id = "powerapi-core", base = file("powerapi-core"))
  lazy val powerapiCli = Project(id = "powerapi-cli", base = file("powerapi-cli")).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)
  lazy val powerapiDaemon = Project(id = "powerapi-daemon", base = file("powerapi-daemon")).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)
  lazy val powerapiSampling = Project(id = "powerapi-sampling", base = file("powerapi-sampling")).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)
  lazy val powerapiHpcAnalysis = Project(id = "powerapi-hpc-analysis", base = file("powerapi-hpc-analysis")).dependsOn(powerapiCore % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)
}
