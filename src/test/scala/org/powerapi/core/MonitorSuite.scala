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

class MonitorMockSubscriber(eventBus: MessageBus) extends Actor {
  import MonitorChannel.{ subscribeTarget, MonitorTarget }

  override def preStart() = {
    subscribeTarget(eventBus)(self)
  }

  def receive = active(0)

  def active(acc: Int): Actor.Receive = {
    case _: MonitorTarget => context become active(acc + 1)
    case "reset" => context become active(0)
    case "get" => sender ! acc
  }
}

class MonitorSuite(system: ActorSystem) extends UnitTest(system) {
  import MonitorChannel.{ formatMonitorName, startMonitor, stopMonitor }
  import MonitorChannel.{ MonitorStart, MonitorStop}

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("MonitorSuite"))

  val eventListener = ConfigFactory.parseResources("test.conf")

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "The Monitor actors" should "launch an exception when the messages received cannot be handled" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest1", eventListener)

    val suid = UUID.randomUUID()
    val frequency = 50.milliseconds
    val targets = List(Process(1))
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, suid, frequency, targets), "monitor1")
    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors1")

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", suid)
    })(_system)

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", suid, Duration.Zero, targets)
    })(_system)

    // Not an exception, just an assessment (switching in the running state).
    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", suid, frequency, targets)
    })(_system)

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", suid, frequency, targets)
    })(_system)

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", UUID.randomUUID())
    })(_system)

    EventFilter.warning(occurrences = 1, source = monitors.path.toString).intercept({
      stopMonitor(suid)(eventBus)
    })(_system)

    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A MonitorChild actor" should "start to listen ticks for its frequency and produce messages" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest2", eventListener)
    val clock = _system.actorOf(Props(classOf[Clock], eventBus), "clock2")

    val frequency = 25.milliseconds
    val suid = UUID.randomUUID()
    val targets = List(Process(1), Application("java"), All)
    
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, suid, frequency, targets), "monitor2")
    val subscriber = _system.actorOf(Props(classOf[MonitorMockSubscriber], eventBus))
    val watcher = TestProbe()(_system)
    watcher.watch(monitor)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", suid, frequency, targets)
    })(_system)

    Thread.sleep(250)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", suid)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(monitor)
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
    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it can "handle a large number of targets" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest3")

    val frequency = 25.milliseconds
    val suid = UUID.randomUUID()
    val targets = scala.collection.mutable.ListBuffer[Target]()

    for(i <- 1 to 100) {
      targets += Process(i)
    }

    val clock = _system.actorOf(Props(classOf[Clock], eventBus), "clock3")
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, suid, frequency, targets.toList), "monitor3")
    val subscriber = _system.actorOf(Props(classOf[MonitorMockSubscriber], eventBus))
    val watcher = TestProbe()(_system)
    watcher.watch(monitor)

    monitor ! MonitorStart("test", suid, frequency, targets.toList)
    Thread.sleep(250)
    monitor ! MonitorStop("test", suid)

    awaitAssert({
      watcher.expectTerminated(monitor)
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
    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A Monitors actor" should "handle its MonitorChild actors and subscribers have to receive messages" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest4")
    val clock = _system.actorOf(Props(classOf[Clock], eventBus), "clock4")
    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors4")

    val monitor = new Monitor(eventBus)
    val frequency = 25.milliseconds
    val targets = List(Process(1), Application("java"))

    val subscribers = scala.collection.mutable.ListBuffer[ActorRef]()

    for(i <- 0 until 100) {
      subscribers += _system.actorOf(Props(classOf[MonitorMockSubscriber], eventBus))
    }

    startMonitor(monitor.suid, frequency, targets)(eventBus)
    Thread.sleep(250)
    monitor.cancel

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatMonitorName(monitor.suid)).resolveOne(), timeout.duration)
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
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)  
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it should "handle a large number of monitors" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest5")
    val clock = _system.actorOf(Props(classOf[Clock], eventBus), "clock5")
    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors5")

    val targets = List(All)
    val subscriptions = scala.collection.mutable.ListBuffer[Monitor]()

    for(frequency <- 50 to 100) {
      val subscription = new Monitor(eventBus)
      subscriptions += subscription
      startMonitor(subscription.suid, frequency.milliseconds, targets)(eventBus)
    }

    Thread.sleep(1000)

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }
}
