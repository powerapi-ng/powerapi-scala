name := "powerapi"

version in ThisBuild := "3.0"

scalaVersion in ThisBuild := "2.11.4"

scalacOptions in ThisBuild ++= Seq(
  "-language:reflectiveCalls",
  "-language:implicitConversions",
  "-feature",
  "-deprecation"
)

// Logging
libraryDependencies in ThisBuild ++= Seq(
  "org.apache.logging.log4j" % "log4j-api" % "2.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.1"
)

parallelExecution in (ThisBuild, Test) := false

codacyProjectTokenFile := Some("./codacy-token.txt")
