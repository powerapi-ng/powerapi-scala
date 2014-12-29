name := "powerapi"

version := "3.0"

scalaVersion := "2.11.4"

// App
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe" % "config" % "1.2.1",
  "fr.inria.powerspy" %% "powerspy-scala" % "1.0.0"
)

// Logging
libraryDependencies ++= Seq(
  "org.apache.logging.log4j" % "log4j-api" % "2.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.1"
)

// Tests
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "org.scalatest" %% "scalatest" % "2.2.2" % "test"
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation"
)

parallelExecution in Test := false

instrumentSettings
