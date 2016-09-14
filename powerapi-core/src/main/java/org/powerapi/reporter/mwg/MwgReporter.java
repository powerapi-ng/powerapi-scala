package org.powerapi.reporter.mwg;

import org.mwg.*;
import org.mwg.core.utility.CoreDeferCounterSync;
import org.mwg.ml.MLPlugin;
import org.mwg.ml.algorithm.regression.PolynomialNode;
import org.mwg.task.Task;
import org.powerapi.PowerDisplay;
import org.powerapi.module.PowerChannel;
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
    //node store in (global) var
    private final String measurementNode = "measurementNode";
    private final String methodNode = "methodNode";
    private final String powerNode = "powerNode";
    private final String threadNode = "threadNode";
    private final String cpuNode = "cpuNode";

    //var to set at the beginning, i.e. before run the tasks
    private final String methodName = "methodName";
    private final String threadID = "threadID";
    private final String cpuID = "cpuID";
    private final String timeStamp = "timeStamp";
    private final String powerValue = "powerValue";

    private final Task finalTask;


    public MwgReporter(String dbPath, String measurement) {
        GraphBuilder graphBuilder = new GraphBuilder()
                .withStorage(new LevelDBStorage(dbPath))
                .withPlugin(new MLPlugin())
                .withPlugin(new NodeAggregatorPlugin());
        graph = graphBuilder.build();
        this.measurement = measurement;

        finalTask = newTask();
        initTasks();
    }

    private void initTasks() {
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
                        .add(Method.REL_POWER,powerNode)
                        .fromVar(powerNode);
        Task getOrCreatePower =
                fromVar(methodNode)
                        .traverse(Method.REL_POWER)
                        .ifThen(context -> context.result().size() == 0, createPowerNode)
                        .asGlobalVar(powerNode);
        Task getOrCreateMethod =
                fromVar(measurementNode)
                        .traverseIndex(Measurement.IDX_REL_METHOD, Method.ATT_NAME + "=" + "{{" + methodName + "}}")
                        .ifThen(context -> context.result().size() == 0,createMethod)
                        .asGlobalVar(methodNode)
                        .subTask(getOrCreatePower)
                        .fromVar(methodNode);

        Task createThread =
                newNode()
                        .setProperty(Thread.ATT_THREADID, Thread.ATT_THREADID_TYPE,"{{" + threadID + "}}")
                        .setProperty(Debug.ATT_ID,Debug.ATT_ID_TYPE,(Debug.nextIndex++) + "")
                        .indexNode(Debug.IDX_DEBUG,Debug.ATT_ID + "")
                        .asGlobalVar(threadNode)
                        .fromVar(methodNode)
                        .localIndex(Method.IDX_REL_THREAD,Thread.ATT_THREADID,threadNode)
                        .newTypedNode(Measure.NODE_TYPE)
                        .asVar("measureNode")
                        .fromVar(threadNode)
                        .add(Thread.REL_MEASURE,"measureNode")
                        .fromVar(powerNode)
                        .add(PolynomialAggregatorNode.REL_CHILD,"measureNode")
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
                        .traverse(Thread.REL_MEASURE)
                        .jump("{{" + timeStamp + "}}")
                        .setProperty(PolynomialNode.VALUE, Type.DOUBLE,"{{" + powerValue + "}}");

        finalTask
                .setTime("0")
                .setWorld("0")
                .subTask(getOrCreateSoftMeasured)
                .subTask(getOrCreateMethod)
                .subTask(getOrCreateThread)
                .subTask(updateCpuIfNeeded)
                .subTask(addCPUValue)
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
        Seq<PowerChannel.RawPowerReport> seqRawPower = aggregatePowerReport.rawPowers();
        scala.collection.Iterator<PowerChannel.RawPowerReport> it = seqRawPower.toIterator();
        PowerChannel.RawPowerReport rawPowerReport;
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
                        .subTask(finalTask)
                        .execute(graph,null);
            }
        }



    }
}
