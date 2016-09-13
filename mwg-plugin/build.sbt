name := "mwg-plugin"

organization := "org.powerapi"

resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"

libraryDependencies ++= Seq(
  "org.kevoree.mwg" % "api" % "7-SNAPSHOT",
  "org.kevoree.mwg" % "core" % "7-SNAPSHOT",
  "org.kevoree.mwg.plugins" % "ml" % "7-SNAPSHOT"

)

licenses := Seq("AGPL-3.0" -> url("http://www.gnu.org/licenses/agpl-3.0.txt"))

publishMavenStyle := true