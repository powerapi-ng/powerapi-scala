name := "powerapi-hpc-analysis"

// App
libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-math3" % "3.4.1"
)

lazy val downloadBluecoveHpcAnalysis = taskKey[File]("download-bluecove-hpc-analysis")
lazy val downloadBluecoveGplHpcAnalysis = taskKey[File]("download-bluecove-gpl-hpc-analysis")

downloadBluecoveHpcAnalysis := {
  val locationBluecove = baseDirectory.value / "lib" / "bluecove-2.1.0.jar"
  if(!locationBluecove.getParentFile.exists()) locationBluecove.getParentFile.mkdirs()
  if(!locationBluecove.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-2.1.0.jar"), locationBluecove)
  locationBluecove
}

downloadBluecoveGplHpcAnalysis := {
  val locationBluecoveGpl = baseDirectory.value / "lib" / "bluecove-gpl-2.1.0.jar"
  if(!locationBluecoveGpl.getParentFile.exists()) locationBluecoveGpl.getParentFile.mkdirs()
  if(!locationBluecoveGpl.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-gpl-2.1.0.jar"), locationBluecoveGpl)
  locationBluecoveGpl
}

mappings in Universal += downloadBluecoveHpcAnalysis.value -> s"lib/${downloadBluecoveHpcAnalysis.value.name}"

mappings in Universal += downloadBluecoveGplHpcAnalysis.value -> s"lib/${downloadBluecoveGplHpcAnalysis.value.name}"

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
