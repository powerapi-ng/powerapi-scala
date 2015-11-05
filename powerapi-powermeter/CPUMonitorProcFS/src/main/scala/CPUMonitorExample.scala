import org.powerapi.PowerMeter
import org.powerapi.module.cpu.simple.ProcFSCpuSimpleModule
import org.powerapi.core.target._
import org.powerapi.reporter.ConsoleDisplay
import scala.concurrent.duration._

object Monitor extends App {
  val cpu = PowerMeter.loadModule(ProcFSCpuSimpleModule())
  val console = new ConsoleDisplay
  val monitoring = cpu.monitor(1.second)(All) to console

  cpu.waitFor(5.minutes)
  
  monitoring.cancel
  cpu.shutdown
}

