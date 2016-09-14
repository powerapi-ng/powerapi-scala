package org.powerapi.reporter.mwg.plugins;

import org.mwg.Graph;
import org.mwg.Node;
import org.mwg.plugin.AbstractPlugin;
import org.mwg.plugin.NodeFactory;

public class NodeAggregatorPlugin extends AbstractPlugin {

    public NodeAggregatorPlugin() {
        declareNodeType(PolynomialAggregatorNode.NAME, new NodeFactory() {
            @Override
            public Node create(long world, long time, long id, Graph graph) {
                return new PolynomialAggregatorNode(world,time,id,graph);
            }
        });
    }
}
