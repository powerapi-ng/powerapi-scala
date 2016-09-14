name := "mwg-plugin"

organization := "org.powerapi"

/*resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "org.kevoree.mwg" % "api" % "7" changing(),
  "org.kevoree.mwg" % "core" % "7" changing(),
  "org.kevoree.mwg.plugins" % "ml" % "7" changing()

)*/

//it should be a jar-with-dependencies
unmanagedJars in Compile ++= {
  baseDirectory.value
  val m2 = Path.userHome / ".m2/repository"
  val baseDirectories = (m2 / "org/kevoree/mwg/core/7-SNAPSHOT") +++ (m2 / "org/kevoree/mwg/api/7-SNAPSHOT") +++ (m2 / "org/kevoree/mwg/plugins/ml/7-SNAPSHOT")
  val customJars = (baseDirectories ** "*.jar")
  customJars.classpath
}

licenses := Seq("AGPL-3.0" -> url("http://www.gnu.org/licenses/agpl-3.0.txt"))

publishMavenStyle := true

crossPaths := false