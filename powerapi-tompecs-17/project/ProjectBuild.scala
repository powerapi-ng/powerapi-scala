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
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import sbt.Keys._
import sbt._

object ProjectBuild extends Build {

  lazy val rootSettings = Seq(
    version := "1.0",
    scalaVersion := "2.11.7",
    scalacOptions := Seq(
      "-language:reflectiveCalls",
      "-language:implicitConversions",
      "-feature",
      "-deprecation"
    )
  )

  lazy val tompecs16 = Project(id = "powerapi-tompecs-17", base = file(".")).settings(rootSettings: _*).aggregate(expProcessLevelXeon, expDomainPowerModelTegra4C, expDomainPowerModelTegra1C, expSystemImpactXeon, hpcAnalysisDefault)

  lazy val expProcessLevelXeon = Project(id = "exp-process-level-xeon", base = file("exp-process-level-xeon")).settings(rootSettings: _*).enablePlugins(JavaAppPackaging)
  lazy val expDomainPowerModelTegra4C = Project(id = "exp-domain-pmodel-tegra-4c", base = file("exp-domain-pmodel-tegra-4c")).settings(rootSettings: _*).enablePlugins(JavaAppPackaging)
  lazy val expDomainPowerModelTegra1C = Project(id = "exp-domain-pmodel-tegra-1c", base = file("exp-domain-pmodel-tegra-1c")).settings(rootSettings: _*).enablePlugins(JavaAppPackaging)
  lazy val expSystemImpactXeon = Project(id = "exp-system-impact-xeon", base = file("exp-system-impact-xeon")).settings(rootSettings: _*).enablePlugins(JavaAppPackaging)

  lazy val hpcAnalysis = Project(id = "hpc-analysis", base = file("hpc-analysis")).settings(rootSettings: _*)
  lazy val hpcAnalysisDefault = Project(id = "hpc-analysis-default", base = file("hpc-analysis/default")).settings(rootSettings: _*).dependsOn(hpcAnalysis).enablePlugins(JavaAppPackaging)
  lazy val hpcAnalysisArm = Project(id = "hpc-analysis-arm", base = file("hpc-analysis/arm")).settings(rootSettings: _*).dependsOn(hpcAnalysis).enablePlugins(JavaAppPackaging)
}
