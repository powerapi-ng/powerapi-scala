package org.powerapi.core

import org.powerapi.test.UnitTest

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, DurationInt }

import akka.actor.{ Actor, ActorSystem, Props }
import akka.pattern.gracefulStop
import akka.testkit.{ EventFilter, TestKit, TestProbe }
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

class ProcessMockSubscriber extends Actor {
  import SubscriptionChannel.{ subscribeProcess, SubscriptionApp, SubscriptionProcess }

  override def preStart() = {
    subscribeProcess(self)
  }

  def receive = active(0)

  def active(acc: Int): Actor.Receive = {
    case _: SubscriptionProcess | _: SubscriptionApp => context become active(acc + 1)
    case "reset" => context become active(0)
    case "get" => sender ! acc
  }
}

class AllMockSubscriber extends Actor {
  import SubscriptionChannel.{ subscribeAll, SubscriptionAll }

  override def preStart() = {
    subscribeAll(self)
  }

  def receive = active(0)

  def active(acc: Int): Actor.Receive = {
    case _: SubscriptionAll => context become active(acc + 1)
    case "reset" => context become active(0)
    case "get" => sender ! acc
  }
}

class SubscriptionSuite(system: ActorSystem) extends UnitTest(system) {
  import SubscriptionChannel.{ SubscriptionStart, SubscriptionStop }
  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SubscriptionSuite"))

  val eventListener = ConfigFactory.parseResources("test.conf")

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "The Subscription actors" should "launch an exception when the messages received cannot be handled" in {
    val _system = ActorSystem("SubscriptionSuiteTest1", eventListener)

    val frequency = 50.milliseconds
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], "1", frequency, List(ALL)), "subchild1")

    EventFilter.warning(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStop("test", "1")
    })(_system)

    EventFilter.warning(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", "1", Duration.Zero, List(ALL))
    })(_system)

    EventFilter.info(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", "1", frequency, List(ALL))
    })(_system)

    EventFilter.warning(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", "1", frequency, List(ALL))
    })(_system)

    EventFilter.warning(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStop("test", "2")
    })(_system)

    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    _system.shutdown()
  }

  "A SubscriptionChild actor" should "start to listen ticks for its frequency and produce messages" in {
    val _system = ActorSystem("SubscriptionSuiteTest2", eventListener)
    val _system2 = ActorSystem("SubscriptionSuiteTest3")

    val frequency = 25.milliseconds
    val suid = "1"
    val targets = List(Process(1), Application("java"))
    val clock = _system.actorOf(Props(classOf[Clock]), "clock2")
    val clock2 = _system2.actorOf(Props(classOf[Clock]), "clock3")
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], suid, frequency, targets ++ List(ALL)), "subchild2")
    val processMock = _system.actorOf(Props(classOf[ProcessMockSubscriber]))
    val allMock = _system.actorOf(Props(classOf[AllMockSubscriber]))

    EventFilter.info(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", suid, frequency, targets ++ List(ALL))
    })(_system)

    Thread.sleep(500)

    processMock ! "get"
    expectMsgClass(classOf[Int]) should be >= ((targets.size * 20) - 5)
    allMock ! "get"
    expectMsgClass(classOf[Int]) should be >= (20 - 5)

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(processMock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(allMock, timeout.duration), timeout.duration)
    _system.shutdown()
  }

  it should "stop to produce messages" in {
    val _system = ActorSystem("SubscriptionSuiteTest3")

    val frequency = 25.milliseconds
    val suid = "1"
    val targets = List(Process(1), Application("java"))
    val clock = _system.actorOf(Props(classOf[Clock]), "clock3")
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], suid, frequency, targets ++ List(ALL)), "subchild3")
    val watcher = TestProbe()(_system)
    watcher.watch(subsChild)

    subsChild ! SubscriptionStart("test", suid, frequency, targets ++ List(ALL))
    subsChild ! SubscriptionStop("test", suid)
    watcher.expectTerminated(subsChild)

    val processMock = _system.actorOf(Props(classOf[ProcessMockSubscriber]))
    val allMock = _system.actorOf(Props(classOf[AllMockSubscriber]))

    Thread.sleep(200)

    processMock ! "get"
    expectMsgClass(classOf[Int]) should equal(0)
    allMock ! "get"
    expectMsgClass(classOf[Int]) should equal(0)

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(processMock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(allMock, timeout.duration), timeout.duration)
    _system.shutdown()
  }

  it can "handle a large number of targets" in {
    val _system = ActorSystem("SubscriptionSuiteTest3")

    val frequency = 25.milliseconds
    val suid = "1"
    val targets = scala.collection.mutable.ListBuffer[Target]()

    for(i <- 1 to 100) {
      targets += Process(i)
    }

    val clock = _system.actorOf(Props(classOf[Clock]), "clock3")
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], suid, frequency, targets.toList), "subchild3")
    val processMock = _system.actorOf(Props(classOf[ProcessMockSubscriber]))
    val allMock = _system.actorOf(Props(classOf[AllMockSubscriber]))

    subsChild ! SubscriptionStart("test", suid, frequency, targets.toList)
    
    Thread.sleep(500)

    processMock ! "get"
    expectMsgClass(classOf[Int]) should be >= ((targets.size * 20) - 5)
    allMock ! "get"
    expectMsgClass(classOf[Int]) should equal(0)

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(processMock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(allMock, timeout.duration), timeout.duration)
    _system.shutdown()
  }
}