import org.powerapi.PowerMeter;
import org.powerapi.PowerModule;
import org.powerapi.PowerMonitoring;
import org.powerapi.module.cpu.simple.SigarCpuSimpleModule;
import org.powerapi.reporter.JFreeChartDisplay;
import org.powerapi.core.target.Target;
import org.powerapi.core.target.Application;
import scala.concurrent.duration.Duration;
import scala.collection.JavaConversions;
import java.util.ArrayList;
import java.util.Arrays;

public class AppMonitorExample {
  public static void main (String[] args) {
    ArrayList<PowerModule> modules = new ArrayList<PowerModule>(Arrays.asList(SigarCpuSimpleModule.apply()));
    PowerMeter cpu_simple = PowerMeter.loadModule(JavaConversions.asScalaBuffer(modules));
    JFreeChartDisplay chart = new JFreeChartDisplay();
    ArrayList<Target> targets = new ArrayList<Target>(Arrays.asList(new Application(args[0])));
    PowerMonitoring monitoring = cpu_simple.monitor(Duration.create(1L, "seconds"), JavaConversions.asScalaBuffer(targets));
    monitoring.to(chart);
    
    cpu_simple.waitFor(Duration.create(5, "minutes"));
    
    monitoring.cancel();
    cpu_simple.shutdown();
  }
}

