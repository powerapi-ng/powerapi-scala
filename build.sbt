name := "powerapi"

version in ThisBuild := "3.0"

scalaVersion in ThisBuild := "2.11.4"

scalacOptions in ThisBuild ++= Seq(
  "-language:reflectiveCalls",
  "-language:implicitConversions",
  "-feature",
  "-deprecation"
)

parallelExecution in (ThisBuild, Test) := false

instrumentSettings
