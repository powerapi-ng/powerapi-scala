package org.powerapi.reporter.mwg.plugins;

import org.mwg.Callback;
import org.mwg.Graph;
import org.mwg.Node;
import org.mwg.core.utility.CoreDeferCounterSync;
import org.mwg.ml.algorithm.regression.PolynomialNode;
import org.mwg.plugin.AbstractNode;

public class PolynomialAggregatorNode extends AbstractNode implements Node{

    public static final String ATT_VALUE = "value";
    public static final String REL_CHILD = "child";

    public static final String NAME = "PolynomialAggregatorNodeTest";

    public PolynomialAggregatorNode(long p_world, long p_time, long p_id, Graph p_graph) {
        super(p_world, p_time, p_id, p_graph);
    }

    @Override
    public void setProperty(String propertyName, byte propertyType, Object propertyValue) {
        throw new RuntimeException("PolynomialAggregatorNode node has no settable value. Please read the documentation about " +
                "how it works");
    }

    @Override
    public Object get(String propertyName) {
        if(propertyName.equals(ATT_VALUE)) {
            final CoreDeferCounterSync counter = new CoreDeferCounterSync(1);
            final Callback callback = counter.wrap();
            this.rel(REL_CHILD, new Callback<Node[]>() {
                @Override
                public void on(Node[] result) {
                    //todo should be optimize
                    double sum = 0;
                    for (int i = 0; i < result.length; i++) {
                        sum = sum + (Double) result[i].get(PolynomialNode.VALUE);
                    }
                    callback.on(sum);
                    counter.count();
                }
            });

            return counter.waitResult();
        }

        throw new RuntimeException("PolynomialAggregatorNode node has one reachable property: " + ATT_VALUE
                +". Please read the documentation about how it works");
    }






}
