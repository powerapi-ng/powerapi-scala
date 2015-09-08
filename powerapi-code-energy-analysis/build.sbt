name := "powerapi-code-energy-analysis"

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
