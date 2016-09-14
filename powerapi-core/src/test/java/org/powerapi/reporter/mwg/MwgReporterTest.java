package org.powerapi.reporter.mwg;

import org.mwg.Callback;
import org.mwg.Node;
import org.mwg.ml.algorithm.regression.PolynomialNode;
import org.powerapi.core.power.PowerConverter;
import org.powerapi.core.power.RawPower;
import org.powerapi.core.target.Application;
import org.powerapi.module.PowerChannel;
import org.powerapi.module.libpfm.AgentTick;
import org.powerapi.module.libpfm.PCInterruptionChannel;
import org.powerapi.module.libpfm.PayloadProtocol;
import org.powerapi.module.libpfm.TID;
import org.powerapi.reporter.mwg.model.Measurement;
import org.powerapi.reporter.mwg.model.Method;
import org.powerapi.reporter.mwg.model.Thread;
import org.powerapi.reporter.mwg.plugins.PolynomialAggregatorNode;
import scala.collection.mutable.Seq;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MwgReporterTest {

    public static void main(String[] args) {
        String dbPath = "./dbTest";
        MwgReporter mwgReporter = new MwgReporter(dbPath,"testSoft");
        mwgReporter.connect();

        UUID uuid = UUID.randomUUID();
        PowerChannel.AggregatePowerReport report1 = new PowerChannel.AggregatePowerReport(uuid) {
            @Override
            public Seq<PowerChannel.RawPowerReport> rawPowers() {
                List<PowerChannel.RawPowerReport> list = new ArrayList<>();

                PowerChannel.RawPowerReport r1 = new PowerChannel.RawPowerReport("",uuid,new Application("testSoft"),
                        new RawPower(10, PowerConverter.WATTS()),"cpu",new PCInterruptionChannel.InterruptionTick("",0,new TID(10),"a.b.b",12344L,true));
                list.add(r1);

                PowerChannel.RawPowerReport r2 = new PowerChannel.RawPowerReport("",uuid,new Application("testSoft"),
                        new RawPower(20, PowerConverter.WATTS()),"disk",new AgentTick("",12344L, PayloadProtocol.Payload.newBuilder().setCore(0).setPid(10).setTid(10).setTimestamp(12344L).build()));
                list.add(r2);

                return scala.collection.JavaConversions.asScalaBuffer(list).seq();
            }
        };
        mwgReporter.display(report1);

        PowerChannel.AggregatePowerReport report2 = new PowerChannel.AggregatePowerReport(uuid) {
            @Override
            public Seq<PowerChannel.RawPowerReport> rawPowers() {
                List<PowerChannel.RawPowerReport> list = new ArrayList<>();

                PowerChannel.RawPowerReport r1 = new PowerChannel.RawPowerReport("",uuid,new Application("testSoft"),
                        new RawPower(11, PowerConverter.WATTS()),"cpu",new PCInterruptionChannel.InterruptionTick("",0,new TID(10),"a.b.b",12345L,false));
                list.add(r1);

                PowerChannel.RawPowerReport r2 = new PowerChannel.RawPowerReport("",uuid,new Application("testSoft"),
                        new RawPower(10, PowerConverter.WATTS()),"cpu",new PCInterruptionChannel.InterruptionTick("",1,new TID(11),"w.x.y",12345L,true));
                list.add(r2);

                PowerChannel.RawPowerReport r3 = new PowerChannel.RawPowerReport("",uuid,new Application("testSoft"),
                        new RawPower(8, PowerConverter.WATTS()),"disk",new AgentTick("",12344L, PayloadProtocol.Payload.newBuilder().setCore(1).setPid(11).setTid(11).setTimestamp(12345L).build()));
                list.add(r3);

                return scala.collection.JavaConversions.asScalaBuffer(list).seq();
            }
        };
        mwgReporter.display(report2);

       PowerChannel.AggregatePowerReport report3 = new PowerChannel.AggregatePowerReport(uuid) {
            @Override
            public Seq<PowerChannel.RawPowerReport> rawPowers() {
                List<PowerChannel.RawPowerReport> list = new ArrayList<>();

                PowerChannel.RawPowerReport r1 = new PowerChannel.RawPowerReport("",uuid,new Application("testSoft"),
                        new RawPower(1, PowerConverter.WATTS()),"cpu",new PCInterruptionChannel.InterruptionTick("",0,new TID(12),"w.x.y",12346L,true));
                list.add(r1);

                PowerChannel.RawPowerReport r2 = new PowerChannel.RawPowerReport("",uuid,new Application("testSoft"),
                        new RawPower(1, PowerConverter.WATTS()),"disk",new AgentTick("",12346L, PayloadProtocol.Payload.newBuilder().setCore(0).setPid(12).setTid(12).setTimestamp(12346L).build()));
                list.add(r2);

                return scala.collection.JavaConversions.asScalaBuffer(list).seq();
            }
        };
        mwgReporter.display(report3);


        mwgReporter.getGraph().findAll(0, 12346L, Measurement.IDX_MEASUREMENT, new Callback<Node[]>() {
            @Override
            public void on(Node[] measurement) {

                for(Node m : measurement) {
                    System.out.println("Measurement: " + m);
                    m.findAll(Measurement.IDX_REL_METHOD, new Callback<Node[]>() {
                        @Override
                        public void on(Node[] methods) {

                            for (int i = 0; i < methods.length; i++) {
                                System.out.println("\tMethod: " + methods[i]);
                                methods[i].rel(Method.REL_POWER, new Callback<Node[]>() {
                                    @Override
                                    public void on(Node[] power) {
                                        for (int p = 0; p < power.length; p++) {
                                            System.out.println("\t\tPower value: " + power[p] + " => " + power[p].get(PolynomialAggregatorNode.ATT_VALUE));
                                        }
                                    }
                                });

                                methods[i].rel(Method.REL_DISK, new Callback<Node[]>() {
                                    @Override
                                    public void on(Node[] disk) {
                                        for (int d = 0; d < disk.length; d++) {
                                            System.out.println("\t\tDisk value: " + disk[d] + " => " + disk[d].get(PolynomialAggregatorNode.ATT_VALUE));
                                        }
                                    }
                                });

                                methods[i].findAll(Method.IDX_REL_THREAD, new Callback<Node[]>() {
                                    @Override
                                    public void on(Node[] thread) {
                                        for (Node t : thread) {
                                            System.out.println("\t\t\tThread: " + t);
                                            t.rel(Thread.REL_CPU_MEASURE, new Callback<Node[]>() {
                                                @Override
                                                public void on(Node[] measure) {
                                                    for (Node m : measure) {
                                                        System.out.println("\t\t\t\tCpu Measure: " + m + " => " + m.get(PolynomialNode.VALUE));
                                                    }
                                                }
                                            });

                                            t.rel(Thread.REL_DISK_MEASURE, new Callback<Node[]>() {
                                                @Override
                                                public void on(Node[] measure) {
                                                    for (Node m : measure) {
                                                        System.out.println("\t\t\t\tDisk Measure: " + m + " => " + m.get(PolynomialNode.VALUE));
                                                    }
                                                }
                                            });
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            }
        });



        Runtime.getRuntime().addShutdownHook(new java.lang.Thread(new Runnable() {
            @Override
            public void run() {
                mwgReporter.disconnect();
                File db = new File(dbPath);
                if(db.exists()) {
                    try {
                        Files.walkFileTree(Paths.get(dbPath), new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }));
    }
}
