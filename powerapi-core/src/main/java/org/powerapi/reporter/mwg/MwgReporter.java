package org.powerapi.reporter.mwg;

import org.mwg.*;
import org.mwg.core.utility.CoreDeferCounterSync;
import org.mwg.ml.MLPlugin;
import org.mwg.ml.algorithm.regression.PolynomialNode;
import org.mwg.task.Task;
import org.powerapi.PowerDisplay;
import org.powerapi.module.PowerChannel;
import org.powerapi.module.libpfm.AgentTick;
import org.powerapi.module.libpfm.PCInterruptionChannel;
import org.powerapi.reporter.mwg.model.*;
import org.powerapi.reporter.mwg.model.Thread;
import org.powerapi.reporter.mwg.plugins.NodeAggregatorPlugin;
import org.powerapi.reporter.mwg.plugins.PolynomialAggregatorNode;
import scala.collection.Seq;

import static org.mwg.task.Actions.*;

public class MwgReporter implements PowerDisplay {

    private final Graph graph;
    private final String measurement;


    //Task data
    //var to set at the beginning, i.e. before run the tasks
    private final String methodName = "methodName";
    private final String threadID = "threadID";
    private final String cpuID = "cpuID";
    private final String timeStamp = "timeStamp";
    private final String powerValue = "powerValue";
    private final String diskValue = "diskValue";

    private final Task finalTask;


    public MwgReporter(String dbPath, String measurement) {
        GraphBuilder graphBuilder = new GraphBuilder()
//                .withStorage(new LevelDBStorage(dbPath))
                .withPlugin(new MLPlugin())
                .withPlugin(new NodeAggregatorPlugin());
        graph = graphBuilder.build();
        this.measurement = measurement;

        finalTask = newTask();
        initTasks();
    }

    private void initTasks() {
        //node store in (global) var
        final String powerNode = "powerNode";
        final String diskNode = "diskNode";
        final String measurementNode = "measurementNode";
        final String methodNode = "methodNode";
        final String threadNode = "threadNode";

        Task createSoftMeasured =
                inject(measurement)
                        .asVar("measurementId")
                        .newNode()
                        .setProperty(Measurement.ATT_MEASUREMENTID, Measurement.ATT_MEASUREMENTID_TYPE,measurement)
                        .indexNode(Measurement.IDX_MEASUREMENT,Measurement.ATT_MEASUREMENTID);
        Task getOrCreateSoftMeasured =
                fromIndex(Measurement.IDX_MEASUREMENT,Measurement.ATT_MEASUREMENTID + "=" + measurement)
                        .ifThen(context -> context.resultAsNodes().size() == 0,createSoftMeasured)
                        .asGlobalVar(measurementNode);

        Task createMethod =
                newNode()
                        .asGlobalVar(methodNode)
                        .setProperty(Method.ATT_NAME,Method.ATT_NAME_TYPE,"{{" + methodName + "}}")
                        .setProperty(Debug.ATT_ID,Debug.ATT_ID_TYPE,(Debug.nextIndex++) + "")
                        .indexNode(Debug.IDX_DEBUG,Debug.ATT_ID + "")
                        .fromVar(measurementNode)
                        .localIndex(Measurement.IDX_REL_METHOD,Method.ATT_NAME,methodNode)
                        .fromVar(methodNode);
        Task createPowerNode =
                newTypedNode(Power.NODE_TYPE)
                        .asGlobalVar(powerNode)
                        .fromVar(methodNode)
                        .add(Method.REL_POWER, powerNode)
                        .fromVar(powerNode);
        Task getOrCreatePower =
                fromVar(methodNode)
                        .traverse(Method.REL_POWER)
                        .ifThen(context -> context.result().size() == 0, createPowerNode)
                        .asGlobalVar(powerNode);
        Task createDiskNode =
                newTypedNode(Power.NODE_TYPE)
                .asGlobalVar(diskNode)
                .fromVar(methodNode)
                .add(Method.REL_DISK,diskNode)
                .fromVar(diskNode);
        Task getOrCreateDisk =
                fromVar(methodNode)
                    .traverse(Method.REL_DISK)
                    .ifThen(context -> context.result().size() == 0, createDiskNode)
                    .asGlobalVar(diskNode);
        Task getOrCreateMethod =
                fromVar(measurementNode)
                        .traverseIndex(Measurement.IDX_REL_METHOD, Method.ATT_NAME + "=" + "{{" + methodName + "}}")
                        .ifThen(context -> context.result().size() == 0,createMethod)
                        .asGlobalVar(methodNode)
                        .subTask(getOrCreatePower)
                        .subTask(getOrCreateDisk)
                        .fromVar(methodNode);

        final String measureCPUNode = "measureCpuNode";
        final String measureDiskNode = "measureDiskNode";
        Task createThread =
                newNode()
                        .setProperty(Thread.ATT_THREADID, Thread.ATT_THREADID_TYPE,"{{" + threadID + "}}")
                        .setProperty(Debug.ATT_ID,Debug.ATT_ID_TYPE,(Debug.nextIndex++) + "")
                        .indexNode(Debug.IDX_DEBUG,Debug.ATT_ID + "")
                        .asGlobalVar(threadNode)
                        //add threadNode in Method.IDX_REL_THREAD relation
                        .fromVar(methodNode)
                        .localIndex(Method.IDX_REL_THREAD,Thread.ATT_THREADID,threadNode)
                        //create Polynomial node for cpu
                        .newTypedNode(Measure.NODE_TYPE)
                        .asVar(measureCPUNode)
                        .fromVar(threadNode)
                        .add(Thread.REL_CPU_MEASURE,measureCPUNode)
                        //add measureNode in 'child' relation of PowerNode
                        .fromVar(powerNode)
                        .add(PolynomialAggregatorNode.REL_CHILD,measureCPUNode)
                        //create polynomial node for disk
                        .newTypedNode(Measure.NODE_TYPE)
                        .asVar(measureDiskNode)
                        .fromVar(threadNode)
                        .add(Thread.REL_DISK_MEASURE,measureDiskNode)
                        //add diskNode in 'child' relation of DiskNode
                        .fromVar(diskNode)
                        .add(PolynomialAggregatorNode.REL_CHILD,measureDiskNode)
                        .fromVar(threadNode);
        Task getOrCreateThread =
                fromVar(methodNode)
                        .traverseIndex(Method.IDX_REL_THREAD, Thread.ATT_THREADID + "=" + "{{" + threadID + "}}")
                        .ifThen(context -> context.result().size() == 0, createThread)
                        .asGlobalVar(threadNode);


        Task updateCpuIfNeeded =
                get(Thread.ATT_CPU_ID)
                        .ifThen(context -> (context.result().size() == 0 || !context.result().get(0).equals(cpuID)),
                                fromVar(threadNode)
                                        .jump("{{" + timeStamp + "}}")
                                        .setProperty(Thread.ATT_CPU_ID,Thread.ATT_CPU_ID_TYPE,cpuID));

        Task addCPUValue =
                fromVar(threadNode)
                        .traverse(Thread.REL_CPU_MEASURE)
                        .jump("{{" + timeStamp + "}}")
                        .setProperty(PolynomialNode.VALUE, Type.DOUBLE,"{{" + powerValue + "}}");

        Task addDiskValue =
                fromVar(threadNode)
                    .traverse(Thread.REL_DISK_MEASURE)
                    .jump("{{" + timeStamp + "}}")
                    .setProperty(PolynomialNode.VALUE,Type.DOUBLE,"{{" + diskValue + "}}");

        finalTask
                .setTime("{{" + timeStamp + "}}")
                .setWorld("0")
                .subTask(getOrCreateSoftMeasured)
                .subTask(getOrCreateMethod)
                .subTask(getOrCreateThread)
                .subTask(updateCpuIfNeeded)
                .subTask(addCPUValue)
                .subTask(addDiskValue)
                .save();
    }


    public void connect() {
        CoreDeferCounterSync counter = new CoreDeferCounterSync(1);
        graph.connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean succeed) {
                if(!succeed) {
                    throw new RuntimeException("Error during graph connection.");
                }
                counter.count();
            }
        });
        counter.waitResult();
    }

    public void disconnect() {
        CoreDeferCounterSync counter = new CoreDeferCounterSync(1);
        graph.disconnect(new Callback<Boolean>() {
            @Override
            public void on(Boolean succeed) {
                if(!succeed) {
                    throw new RuntimeException("Error during graph disconnection.");
                }
                counter.count();
            }
        });

        counter.waitResult();
    }

    public Graph getGraph() {
        return graph;
    }



    @Override
    public void display(PowerChannel.AggregatePowerReport aggregatePowerReport) {
        //sum tick is they are agent
        Seq<PowerChannel.RawPowerReport> seqRawPower = aggregatePowerReport.rawPowers();
        scala.collection.Iterator<PowerChannel.RawPowerReport> itAgent = seqRawPower.toIterator();
        PowerChannel.RawPowerReport rawPowerReport;


        double disk = 0;
        while(itAgent.hasNext()) {
            rawPowerReport = itAgent.next();
            if(rawPowerReport.tick() instanceof AgentTick) {
                disk = disk + rawPowerReport.power().toWatts();
            }
        }

        scala.collection.Iterator<PowerChannel.RawPowerReport> it = seqRawPower.toIterator();
        while(it.hasNext()) {
            rawPowerReport = it.next();
            if(rawPowerReport.tick() instanceof PCInterruptionChannel.InterruptionTick) {
                PCInterruptionChannel.InterruptionTick tick = (PCInterruptionChannel.InterruptionTick) rawPowerReport.tick();

                inject(tick.fullMethodName())
                        .asGlobalVar(methodName)
                        .inject(tick.tid().toString())
                        .asGlobalVar(threadID)
                        .inject(tick.cpu())
                        .asGlobalVar(cpuID)
                        .inject(tick.timestamp())
                        .asGlobalVar(timeStamp)
                        .inject(rawPowerReport.power().toWatts())
                        .asGlobalVar(powerValue)
                        .inject(disk)
                        .asGlobalVar(diskValue)
                        .subTask(finalTask)
                        .execute(graph,null);
            }
        }



    }
}
