/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2016 Inria, University of Lille 1.
 *
 * PowerAPI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * PowerAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with PowerAPI.
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
import sbt._
import Keys._

object BluecovePackaging extends AutoPlugin {

  object autoImport {
    lazy val downloadBluecove = taskKey[File]("download-bluecove-app")
    lazy val downloadBluecoveGpl = taskKey[File]("download-bluecove-gpl-app")
  }

  import autoImport._

  override def projectSettings = Seq(
    downloadBluecove := {
      val locationBluecove = baseDirectory.value.getParentFile / "external-libs" / "bluecove-2.1.0.jar"
      if (!locationBluecove.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-2.1.0.jar"), locationBluecove)
      locationBluecove
    },
    downloadBluecoveGpl := {
      val locationBluecoveGpl = baseDirectory.value.getParentFile / "external-libs" / "bluecove-gpl-2.1.0.jar"
      if (!locationBluecoveGpl.exists()) IO.download(url("https://bluecove.googlecode.com/files/bluecove-gpl-2.1.0.jar"), locationBluecoveGpl)
      locationBluecoveGpl
    }
  )
}
