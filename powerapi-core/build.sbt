name := "powerapi-core"

organization := "org.powerapi"

resolvers ++= Seq(
  "JBoss Thirdparty Uploads" at "https://repository.jboss.org/nexus/content/repositories/thirdparty-uploads/"
)

// App
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe" % "config" % "1.2.1",
  "fr.inria.powerspy" % "powerspy-core_2.11" % "1.2",
  "com.nativelibs4java" % "bridj" % "0.7.0",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "org.jfree" % "jfreechart" % "1.0.19",
  "org.scala-saddle" %% "saddle-core" % "1.3.3",
  "org.hyperic" % "sigar" % "1.6.5.132",
  "io.spray" %% "spray-can" % "1.3.3",
  "io.spray" %% "spray-client" % "1.3.3",
  "io.spray" %% "spray-routing" % "1.3.3",
  "io.spray" %% "spray-json" % "1.3.2",
  "com.github.nscala-time" %% "nscala-time" % "2.0.0",
  "net.java.dev.jna" % "jna" % "4.1.0"
)

// Tests
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "org.scalatest" %% "scalatest" % "2.2.5" % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "test",
  "io.spray" %% "spray-testkit" % "1.3.3" % "test"
)

startYear := Some(2014)

licenses := Seq("AGPL-3.0" -> url("http://www.gnu.org/licenses/agpl-3.0.txt"))

pomExtra := {
  <url>http://www.powerapi.org/</url>
  <scm>
    <url>github.com/Spirals-Team/powerapi</url>
    <connection>scm:git:git@github.com:Spirals-Team/powerapi.git</connection>
    <developerConnection>scm:git:git@github.com:Spirals-Team/powerapi.git</developerConnection>
  </scm>
  <developers>
    <developer>
      <id>mcolmant</id>
      <name>Maxime Colmant</name>
      <url>http://mcolmant.github.io/</url>
    </developer>
    <developer>
      <id>rouvoy</id>
      <name>Romain Rouvoy</name>
      <url>http://www.lifl.fr/~rouvoy/</url>
    </developer>
    <developer>
      <id>lhuertas</id>
      <name>Loïc Huertas</name>
    </developer>
  </developers>
}

publishMavenStyle := true

publishArtifact in Test := false

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

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
