name := "powerapi-cli"

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

scriptClasspath ++= Seq("../conf", "../scripts")

packageName in Universal := name.value

topLevelDirectory := Some(name.value)

executableScriptName := "powerapi"
