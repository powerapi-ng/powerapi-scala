name := "powerapi"

lazy val downloadBluecove = taskKey[File]("download-bluecove-app")
lazy val downloadBluecoveGpl = taskKey[File]("download-bluecove-gpl-app")

val shared = Seq(
  version := "4.2.1",
  scalaVersion := "2.12.1",
  scalacOptions := Seq(
    "-language:existentials",
    "-language:reflectiveCalls",
    "-language:implicitConversions",
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xfatal-warnings"
  ),
  fork := true,
  parallelExecution := false,
  unmanagedBase := root.base.getAbsoluteFile / "external-libs",
  unmanagedClasspath in Test += root.base.getAbsoluteFile  / "external-libs" / "sigar-bin",
  unmanagedClasspath in (Compile, runMain) += root.base.getAbsoluteFile  / "external-libs" / "sigar-bin",
  downloadBluecove := {
    val locationBluecove = root.base.getAbsoluteFile / "external-libs" / "bluecove-2.1.0.jar"
    if (!locationBluecove.exists()) IO.download(url("https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/bluecove/bluecove-2.1.0.jar"), locationBluecove)
    locationBluecove
  },
  downloadBluecoveGpl := {
    val locationBluecoveGpl = root.base.getAbsoluteFile / "external-libs" / "bluecove-gpl-2.1.0.jar"
    if (!locationBluecoveGpl.exists()) IO.download(url("https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/bluecove/bluecove-gpl-2.1.0.jar"), locationBluecoveGpl)
    locationBluecoveGpl
  },
  compile in Compile := (compile in Compile).dependsOn(downloadBluecove, downloadBluecoveGpl).value,
  libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7"
  )
)

lazy val root: sbt.Project = (project in file(".")).aggregate(core, cli, cpuSampling, daemon).settings(shared)

lazy val core = (project in file("powerapi-core")).settings(shared)
lazy val cli = (project in file("powerapi-cli")).settings(shared).dependsOn(core % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)
lazy val cpuSampling = (project in file("powerapi-sampling-cpu")).settings(shared).dependsOn(core % "compile -> compile; test -> test").enablePlugins(JavaAppPackaging)
// NOT MAINTENED...
lazy val daemon = (project in file("powerapi-daemon")).settings(shared).dependsOn(core % "compile -> compile; test -> test").enablePlugins(JavaServerAppPackaging)

// example of power meters
lazy val SDAppProcfsJava =  (project in file("powerapi-powermeter/AppMonitorProcFSJava")).settings(shared).dependsOn(core % "compile -> compile")
lazy val SDAppProcfsScala =  (project in file("powerapi-powermeter/AppMonitorProcFSScala")).settings(shared).dependsOn(core % "compile -> compile")
lazy val SDAppSigarJava =  (project in file("powerapi-powermeter/AppMonitorSigarJava")).settings(shared).dependsOn(core % "compile -> compile")
lazy val SDAppSigarScala =  (project in file("powerapi-powermeter/AppMonitorSigarScala")).settings(shared).dependsOn(core % "compile -> compile")
lazy val SDCpuOmegaWatt =  (project in file("powerapi-powermeter/CPUMonitorOmegaWatt")).settings(shared).dependsOn(core % "compile -> compile")
lazy val SDCpuProcfs =  (project in file("powerapi-powermeter/CPUMonitorProcFS")).settings(shared).dependsOn(core % "compile -> compile")
lazy val SDCpuRAPL =  (project in file("powerapi-powermeter/CPUMonitorRAPL")).settings(shared).dependsOn(core % "compile -> compile")
lazy val SDCpuSigar =  (project in file("powerapi-powermeter/CPUMonitorSigar")).settings(shared).dependsOn(core % "compile -> compile")
