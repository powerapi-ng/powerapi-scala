/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sacha.mavenproject2;

import org.powerapi.core.target.Target;
import java.util.ArrayList;
import java.util.Arrays;
import org.powerapi.PowerMeter;
import org.powerapi.PowerModule;
import org.powerapi.PowerMonitoring;
import org.powerapi.core.target.Application;
import org.powerapi.module.cpu.simple.ProcFSCpuSimpleModule;
import org.powerapi.reporter.JFreeChartDisplay;
import scala.collection.JavaConversions;
import scala.concurrent.duration.Duration;

/**
 *
 * @author sacha
 */

public class mainClass {
    public static void main(String[] args){
    int x=5;
    String y="firefox";
    ArrayList<PowerModule> modules;
    modules = new ArrayList<PowerModule>(Arrays.asList(ProcFSCpuSimpleModule.apply()));
    PowerMeter cpu_simple = PowerMeter.loadModule(JavaConversions.asScalaBuffer(modules));
    JFreeChartDisplay chart = new JFreeChartDisplay();
    ArrayList<Target> targets = new ArrayList<Target>(Arrays.asList(new Application(y)));
    PowerMonitoring monitoring = cpu_simple.monitor(JavaConversions.asScalaBuffer(targets)).every(Duration.create(1L, "seconds"));
    monitoring.to(chart);
    
    cpu_simple.waitFor(Duration.create(x, "minutes"));
    
    monitoring.cancel();
cpu_simple.shutdown();
    }
    
      
    
}
