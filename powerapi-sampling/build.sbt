name := "powerapi-sampling"

NativePackagerKeys.executableScriptName := "sampling"

// App
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "org.scala-saddle" %% "saddle-core" % "1.3.3"
)

// Tests
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "org.scalatest" %% "scalatest" % "2.2.2" % "test"
)

lazy val downloadBluecoveSampling = taskKey[File]("download-bluecove-sampling")
lazy val downloadBluecoveGplSampling = taskKey[File]("download-bluecove-gpl-sampling")

downloadBluecoveSampling := {
  val locationBluecove = baseDirectory.value / "lib" / "bluecove-2.1.0.jar"
  if(!locationBluecove.getParentFile.exists()) locationBluecove.getParentFile.mkdirs()
  if(!locationBluecove.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-2.1.0.jar"), locationBluecove)
  locationBluecove
}

downloadBluecoveGplSampling := {
  val locationBluecoveGpl = baseDirectory.value / "lib" / "bluecove-gpl-2.1.0.jar"
  if(!locationBluecoveGpl.getParentFile.exists()) locationBluecoveGpl.getParentFile.mkdirs()
  if(!locationBluecoveGpl.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-gpl-2.1.0.jar"), locationBluecoveGpl)
  locationBluecoveGpl
}

mappings in Universal += downloadBluecoveSampling.value -> s"lib/${downloadBluecoveSampling.value.name}"

mappings in Universal += downloadBluecoveGplSampling.value -> s"lib/${downloadBluecoveGplSampling.value.name}"

mappings in Universal ++= {
  ((baseDirectory.value.getParentFile * "README*").get map {
    readmeFile: File =>
      readmeFile -> readmeFile.getName
  }) ++
    ((baseDirectory.value.getParentFile * "LICENSE*").get map {
      licenseFile: File =>
        licenseFile -> licenseFile.getName
    })
}

scriptClasspath ++= Seq("../scripts", "../conf")
