name := "powerapi"

version in ThisBuild := "3.3"

scalaVersion in ThisBuild := "2.11.7"

scalacOptions in ThisBuild ++= Seq(
  "-language:reflectiveCalls",
  "-language:implicitConversions",
  "-feature",
  "-deprecation"
)

// Logging
libraryDependencies in ThisBuild ++= Seq(
  "org.apache.logging.log4j" % "log4j-api" % "2.3",
  "org.apache.logging.log4j" % "log4j-core" % "2.3"
)

parallelExecution in (ThisBuild, Test) := false
