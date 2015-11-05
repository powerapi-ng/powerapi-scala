import org.powerapi.PowerMeter
import org.powerapi.module.cpu.simple.ProcFSCpuSimpleModule
import org.powerapi.core.target._
import org.powerapi.reporter.JFreeChartDisplay
import scala.concurrent.duration._

object Monitor extends App {
  val cpu = PowerMeter.loadModule(ProcFSCpuSimpleModule())
  val chart = new JFreeChartDisplay
  val monitoring = cpu.monitor(1.second)(args(0)) to chart

  cpu.waitFor(5.minutes)
  
  monitoring.cancel
  cpu.shutdown
}
