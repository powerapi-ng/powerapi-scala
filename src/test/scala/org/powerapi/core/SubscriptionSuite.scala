package org.powerapi.core

import org.powerapi.test.UnitTest

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }

import akka.actor.{ Actor, ActorIdentity, ActorNotFound, ActorRef, ActorSystem, Identify, Props }
import akka.pattern.gracefulStop
import akka.testkit.{ EventFilter, TestKit, TestProbe }
import akka.util.Timeout

import com.typesafe.config.ConfigFactory

class SubscriptionMockSubscriber(eventBus: MessageBus) extends Actor {
  import SubscriptionChannel.{ subscribeTarget, SubscriptionTarget }

  override def preStart() = {
    subscribeTarget(eventBus)(self)
  }

  def receive = active(0)

  def active(acc: Int): Actor.Receive = {
    case _: SubscriptionTarget => context become active(acc + 1)
    case "reset" => context become active(0)
    case "get" => sender ! acc
  }
}

class SubscriptionSuite(system: ActorSystem) extends UnitTest(system) {
  import SubscriptionChannel.{ formatSubscriptionChildName, startSubscription, stopAllSubscription, stopSubscription }
  import SubscriptionChannel.{ SubscriptionStart, SubscriptionStop}

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SubscriptionSuite"))

  val eventListener = ConfigFactory.parseResources("test.conf")

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "The Subscription actors" should "launch an exception when the messages received cannot be handled" in new Bus {
    val _system = ActorSystem("SubscriptionSuiteTest1", eventListener)

    val suid = UUID.randomUUID()
    val frequency = 50.milliseconds
    val targets = List(Process(1))
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], eventBus, suid, frequency, targets), "subchild1")
    val subsSupervisor = _system.actorOf(Props(classOf[SubscriptionSupervisor], eventBus), "subsup1")

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
      subsChild ! SubscriptionStop("test", UUID.randomUUID())
    })(_system)

    EventFilter.warning(occurrences = 1, source = subsSupervisor.path.toString).intercept({
      stopSubscription(suid)(eventBus)
    })(_system)

    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsSupervisor, timeout.duration), timeout.duration)
    _system.shutdown()
  }

  "A SubscriptionChild actor" should "start to listen ticks for its frequency and produce messages" in new Bus {
    val _system = ActorSystem("SubscriptionSuiteTest2", eventListener)
    val clock = _system.actorOf(Props(classOf[Clock], eventBus), "clock2")

    val frequency = 25.milliseconds
    val suid = UUID.randomUUID()
    val targets = List(Process(1), Application("java"), ALL)
    
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], eventBus, suid, frequency, targets), "subchild2")
    val subscriber = _system.actorOf(Props(classOf[SubscriptionMockSubscriber], eventBus))
    val watcher = TestProbe()(_system)
    watcher.watch(subsChild)

    EventFilter.info(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStart("test", suid, frequency, targets)
    })(_system)

    Thread.sleep(250)

    EventFilter.info(occurrences = 1, source = subsChild.path.toString).intercept({
      subsChild ! SubscriptionStop("test", suid)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(subsChild)
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"/user/clock2/${frequency.toNanos}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    subscriber ! "get"
    // We assume a service quality of 90% (regarding the number of processed messages).
    expectMsgClass(classOf[Int]) should be >= ((targets.size * 10) - (targets.size * 10 * 0.10).toInt)

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
  }

  it can "handle a large number of targets" in new Bus {
    val _system = ActorSystem("SubscriptionSuiteTest3")

    val frequency = 25.milliseconds
    val suid = UUID.randomUUID()
    val targets = scala.collection.mutable.ListBuffer[Target]()

    for(i <- 1 to 100) {
      targets += Process(i)
    }

    val clock = _system.actorOf(Props(classOf[Clock], eventBus), "clock3")
    val subsChild = _system.actorOf(Props(classOf[SubscriptionChild], eventBus, suid, frequency, targets.toList), "subchild3")
    val subscriber = _system.actorOf(Props(classOf[SubscriptionMockSubscriber], eventBus))
    val watcher = TestProbe()(_system)
    watcher.watch(subsChild)

    subsChild ! SubscriptionStart("test", suid, frequency, targets.toList)
    Thread.sleep(250)
    subsChild ! SubscriptionStop("test", suid)

    awaitAssert({
      watcher.expectTerminated(subsChild)
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"/user/clock3/${frequency.toNanos}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    subscriber ! "get"
    // We assume a service quality of 90% (regarding the number of processed messages).
    expectMsgClass(classOf[Int]) should be >= ((targets.size * 10) - (targets.size * 10 * 0.10).toInt)

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsChild, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
  }

  "A SubscriptionSupervisor actor" should "handle its SubscriptionChild actors and subscribers have to receive messages" in new Bus {
    val _system = ActorSystem("SubscriptionSuiteTest4")
    val clock = _system.actorOf(Props(classOf[Clock], eventBus), "clock4")
    val subsSupervisor = _system.actorOf(Props(classOf[SubscriptionSupervisor], eventBus), "subsup4")

    val subscription = new Subscription(eventBus)
    val frequency = 25.milliseconds
    val targets = List(Process(1), Application("java"))

    val subscribers = scala.collection.mutable.ListBuffer[ActorRef]()

    for(i <- 0 until 100) {
      subscribers += _system.actorOf(Props(classOf[SubscriptionMockSubscriber], eventBus))
    }

    startSubscription(subscription.suid, frequency, targets)(eventBus)
    Thread.sleep(250)
    subscription.cancel

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatSubscriptionChildName(subscription.suid)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"/user/clock4/${frequency.toNanos}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

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

  it should "handle a large number of subscriptions" in new Bus {
    val _system = ActorSystem("SubscriptionSuiteTest5")
    val clock = _system.actorOf(Props(classOf[Clock], eventBus), "clock5")
    val subsSupervisor = _system.actorOf(Props(classOf[SubscriptionSupervisor], eventBus), "subsup5")

    val targets = List(ALL)
    val subscriptions = scala.collection.mutable.ListBuffer[Subscription]()

    for(frequency <- 50 to 100) {
      val subscription = new Subscription(eventBus)
      subscriptions += subscription
      startSubscription(subscription.suid, frequency.milliseconds, targets)(eventBus)
    }

    Thread.sleep(1000)

    stopAllSubscription()(eventBus)

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subsSupervisor, timeout.duration), timeout.duration)
    _system.shutdown()
  }
}
