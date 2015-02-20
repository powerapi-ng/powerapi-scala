name := "powerapi-core"

// App
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe" % "config" % "1.2.1",
  "fr.inria.powerspy" % "powerspy-core_2.11" % "1.1",
  "com.nativelibs4java" % "bridj" % "0.6.2",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "org.jfree" % "jfreechart" % "1.0.19",
  "org.scala-saddle" %% "saddle-core" % "1.3.3"
)

// Tests
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "org.scalatest" %% "scalatest" % "2.2.2" % "test"
)

val downloadBluecoveLibs = TaskKey[Seq[File]]("download-bluecove")

downloadBluecoveLibs := {
  val locationBluecove = baseDirectory.value / "lib" / "bluecove-2.1.0.jar"
  val locationBluecoveGpl = baseDirectory.value / "lib" / "bluecove-gpl-2.1.0.jar"
  if(!locationBluecove.getParentFile.exists()) locationBluecove.getParentFile.mkdirs()
  if(!locationBluecoveGpl.getParentFile.exists()) locationBluecoveGpl.getParentFile.mkdirs()
  if(!locationBluecove.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-2.1.0.jar"), locationBluecove)
  if(!locationBluecoveGpl.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-gpl-2.1.0.jar"), locationBluecoveGpl)
  Seq(locationBluecove, locationBluecoveGpl)
}
