name := "powerapi-sampling-cpu"

// App
libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.5",
  "joda-time" % "joda-time" % "2.9.6",
  "com.googlecode.efficient-java-matrix-library" % "ejml" % "0.25"
)

mappings in Universal ++= {
  val dir = baseDirectory.value.getParentFile

  (for {
    (file, relativePath) <- (dir * "README*" --- dir) pair relativeTo (dir)
  } yield file -> s"$relativePath") ++
    (for {
      (file, relativePath) <- (dir * "LICENSE*" --- dir) pair relativeTo (dir)
    } yield file -> s"$relativePath")
}

mappings in Universal ++= {
  val dir = baseDirectory.value.getParentFile / "external-libs" / "sigar-bin"

  for {
    (file, relativePath) <-  (dir.*** --- dir) pair relativeTo(dir)
  } yield file -> s"/lib/sigar-bin/$relativePath"
}

scriptClasspath ++= Seq("../scripts", "../conf")

packageName in Universal := name.value

topLevelDirectory := Some(name.value)

NativePackagerKeys.executableScriptName := "sampling-cpu"
