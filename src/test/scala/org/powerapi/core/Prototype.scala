package org.powerapi

import akka.actor.ActorSystem
import akka.testkit._
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

abstract class UnitTesting(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll