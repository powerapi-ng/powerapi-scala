import scala.concurrent.duration._

import org.powerapi.PowerMeter
import org.powerapi.core.target._
import org.powerapi.module.cpu.simple.ProcFSCpuSimpleModule
import org.powerapi.reporter.JFreeChartDisplay

object AppMonitorExample extends App {
  val cpu = PowerMeter.loadModule(ProcFSCpuSimpleModule())
  val chart = new JFreeChartDisplay
  val monitoring = cpu.monitor(args(0)).every(1.second) to chart

  cpu.waitFor(5.minutes)

  monitoring.cancel()
  cpu.shutdown()
}
