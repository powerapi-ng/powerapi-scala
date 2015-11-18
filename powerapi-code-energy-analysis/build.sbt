name := "powerapi-code-energy-analysis"

// App
libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.1.5"
)

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
