name := "powerapi-sampling"

// App
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.14",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "org.scala-saddle" %% "saddle-core" % "1.3.4"
)

// Tests
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "org.scalatest" %% "scalatest" % "2.2.2" % "test"
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

NativePackagerKeys.executableScriptName := "sampling"
