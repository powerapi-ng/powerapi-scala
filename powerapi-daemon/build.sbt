enablePlugins(JavaServerAppPackaging)

name := "powerapi-daemon"

libraryDependencies ++= Seq(
  "commons-daemon" % "commons-daemon" % "1.0.15"
)

lazy val downloadBluecoveDaemon = taskKey[File]("download-bluecove-daemon")
lazy val downloadBluecoveGplDaemon = taskKey[File]("download-bluecove-gpl-daemon")

downloadBluecoveDaemon := {
  val locationBluecove = baseDirectory.value / "lib" / "bluecove-2.1.0.jar"
  if(!locationBluecove.getParentFile.exists()) locationBluecove.getParentFile.mkdirs()
  if(!locationBluecove.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-2.1.0.jar"), locationBluecove)
  locationBluecove
}

downloadBluecoveGplDaemon := {
  val locationBluecoveGpl = baseDirectory.value / "lib" / "bluecove-gpl-2.1.0.jar"
  if(!locationBluecoveGpl.getParentFile.exists()) locationBluecoveGpl.getParentFile.mkdirs()
  if(!locationBluecoveGpl.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-gpl-2.1.0.jar"), locationBluecoveGpl)
  locationBluecoveGpl
}

mappings in Universal += downloadBluecoveDaemon.value -> s"lib/${downloadBluecoveDaemon.value.name}"

mappings in Universal += downloadBluecoveGplDaemon.value -> s"lib/${downloadBluecoveGplDaemon.value.name}"

mappings in Universal ++= {
  ((file("../") * "README*").get map {
    readmeFile: File =>
      readmeFile -> readmeFile.getName
  }) ++
    ((file("../") * "LICENSE*").get map {
      licenseFile: File =>
        licenseFile -> licenseFile.getName
    })
}

scriptClasspath ++= Seq("../conf", "../scripts")

NativePackagerKeys.executableScriptName := "powerapid"

