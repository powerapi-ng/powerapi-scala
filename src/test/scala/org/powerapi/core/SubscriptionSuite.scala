package org.powerapi.core

import org.powerapi.test.UnitTest

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }

import akka.actor.{ Actor, ActorIdentity, ActorNotFound, ActorRef, ActorSystem, Identify, Props }
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
  import SubscriptionChannel.{ SubscriptionStart, SubscriptionStop, startSubscription, stopAllSubscription, stopSubscription }
  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SubscriptionSuite"))

  val eventListener = ConfigFactory.parseResources("test.conf")

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "The Subscription actors" should "launch an exception when the messages received cannot be handled" in {
    val _system = ActorSystem("SubscriptionSuiteTest1", eventListener)

    val suid = "1"
    val frequency = 50.milliseconds
    val targets = List(Process(1))
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], suid, frequency, targets), "subchild1")
    val subsSupervisor = _system.actorOf(Props(classOf[SubscriptionSupervisor]), "subsup1")

    EventFilter.warning(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStop("test", suid)
    })(_system)

    EventFilter.warning(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", suid, Duration.Zero, targets)
    })(_system)

    // Not an exception, just an assessment (switching in the running state).
    EventFilter.info(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", suid, frequency, targets)
    })(_system)

    EventFilter.warning(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", suid, frequency, targets)
    })(_system)

    EventFilter.warning(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStop("test", "2")
    })(_system)

    EventFilter.warning(occurrences = 1, source = subsSupervisor.path.toString).intercept({
      stopSubscription(suid)
    })(_system)

    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsSupervisor, timeout.duration), timeout.duration)
    _system.shutdown()
  }

  "A SubscriptionChild actor" should "start to listen ticks for its frequency and produce messages" in {
    val _system = ActorSystem("SubscriptionSuiteTest2", eventListener)
    val clock = _system.actorOf(Props(classOf[Clock]), "clock2")

    val frequency = 25.milliseconds
    val suid = "1"
    val targets = List(Process(1), Application("java"))
    
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], suid, frequency, targets ++ List(ALL)), "subchild2")
    val processMock = _system.actorOf(Props(classOf[ProcessMockSubscriber]))
    val allMock = _system.actorOf(Props(classOf[AllMockSubscriber]))
    val watcher = TestProbe()(_system)
    watcher.watch(subsChild)

    EventFilter.info(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", suid, frequency, targets ++ List(ALL))
    })(_system)

    Thread.sleep(250)

    EventFilter.info(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStop("test", suid)
    })(_system)

    within(10.seconds) {
      awaitAssert {
        watcher.expectTerminated(subsChild)
      }
    }

    within(10.seconds) {
      awaitAssert {
        intercept[ActorNotFound] {
          Await.result(_system.actorSelection(s"/user/clock2/${frequency.toNanos}").resolveOne(), timeout.duration)
        }
      }
    }

    processMock ! "get"
    expectMsgClass(classOf[Int]) should be >= (targets.size * 10)
    allMock ! "get"
    expectMsgClass(classOf[Int]) should be >= 10

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(processMock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(allMock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
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
    val watcher = TestProbe()(_system)
    watcher.watch(subsChild)

    subsChild ! SubscriptionStart("test", suid, frequency, targets.toList)
    Thread.sleep(250)
    subsChild ! SubscriptionStop("test", suid)

    within(10.seconds) {
      awaitAssert {
        watcher.expectTerminated(subsChild)
      }
    }

    within(10.seconds) {
      awaitAssert {
        intercept[ActorNotFound] {
          Await.result(_system.actorSelection(s"/user/clock3/${frequency.toNanos}").resolveOne(), timeout.duration)
        }
      }
    }

    processMock ! "get"
    // We assume a service quality of 90% (regarding the number of processed messages).
    expectMsgClass(classOf[Int]) should be >= ((targets.size * 10) - (targets.size * 10 * 0.10).toInt)
    allMock ! "get"
    expectMsgClass(classOf[Int]) should equal(0)

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(processMock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(allMock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
  }

  "A SubscriptionSupervisor actor" should "handle its SubscriptionChild actors and subscribers have to receive messages" in {
    val _system = ActorSystem("SubscriptionSuiteTest4")
    val clock = _system.actorOf(Props(classOf[Clock]), "clock4")
    val subsSupervisor = _system.actorOf(Props(classOf[SubscriptionSupervisor]), "subsup4")

    val subscription = new Subscription
    val frequency = 25.milliseconds
    val targets = List(Process(1), Application("java"))

    val subscribers = scala.collection.mutable.ListBuffer[ActorRef]()

    for(i <- 0 until 100) {
      subscribers += _system.actorOf(Props(classOf[ProcessMockSubscriber]))
    }

    startSubscription(subscription.suid, frequency, targets)
    Thread.sleep(250)
    subscription.cancel

    within(10.seconds) {
      awaitAssert {
        intercept[ActorNotFound] {
          Await.result(_system.actorSelection(s"/user/subsup4/${subscription.suid}").resolveOne(), timeout.duration)
        }
      }
    }

    within(10.seconds) {
      awaitAssert {
        intercept[ActorNotFound] {
          Await.result(_system.actorSelection(s"/user/clock4/${frequency.toNanos}").resolveOne(), timeout.duration)
        }
      }
    }

    for(i <- 0 until 100) {
      subscribers(i) ! "get"
      // We assume a service quality of 90% (regarding the number of processed messages).
      expectMsgClass(classOf[Int]) should be >= ((targets.size * 10) - (targets.size * 10 * 0.10).toInt)
      Await.result(gracefulStop(subscribers(i), timeout.duration), timeout.duration)
    }
    
    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsSupervisor, timeout.duration), timeout.duration)  
    _system.shutdown()
  }

  it should "handle a large number of subscriptions" in {
    val _system = ActorSystem("SubscriptionSuiteTest5")
    val clock = _system.actorOf(Props(classOf[Clock]), "clock5")
    val subsSupervisor = _system.actorOf(Props(classOf[SubscriptionSupervisor]), "subsup5")
    val allMock = _system.actorOf(Props(classOf[AllMockSubscriber]))

    val targets = List(ALL)
    val subscriptions = scala.collection.mutable.ListBuffer[Subscription]()

    // To be sure at least one susbcription actor is started.
    val subscription = new Subscription
    startSubscription(subscription.suid, frequency.milliseconds, targets)
    within(10.seconds) {
      awaitCond {
        _system.actorSelection(s"/user/subsup5/${subscription.suid}") ! Identify(None)
        expectMsgClass(classOf[ActorIdentity]) match {
          case ActorIdentity(_, Some(_)) => true
          case _ => false
        }
      }
    }

    for(frequency <- 50 to 100) {
      val subscription = new Subscription
      subscriptions += subscription
      startSubscription(subscription.suid, frequency.milliseconds, targets)
    }

    Thread.sleep(250)
    stopAllSubscription()

    for(subscription <- subscriptions) {
      within(20.seconds) {
        awaitAssert {
          intercept[ActorNotFound] {
            Await.result(_system.actorSelection(s"/user/subsup5/${subscription.suid}").resolveOne(), timeout.duration)
          }
        }
      }
    }

    allMock ! "get"
    expectMsgClass(classOf[Int]) should not equal(0)
    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsSupervisor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(allMock, timeout.duration), timeout.duration)
    _system.shutdown()
  }
}
