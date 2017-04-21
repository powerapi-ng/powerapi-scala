name := "powerapi-learning"

// App
libraryDependencies ++= Seq(
  "com.github.docker-java" % "docker-java" % "3.0.6",
  "com.github.scopt" %% "scopt" % "3.5.0"
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

scriptClasspath ++= Seq("../conf")

packageName in Universal := name.value

topLevelDirectory := Some(name.value)

executableScriptName := "active-learning"