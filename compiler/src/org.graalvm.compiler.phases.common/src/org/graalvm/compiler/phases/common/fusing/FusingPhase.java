package org.graalvm.compiler.phases.common.fusing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.fusing.Config.Entry;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class FusingPhase extends BasePhase<HighTierContext> {

    private final InliningPhase inliningPhase;

    public FusingPhase(InliningPhase inliningPhase) {
        this.inliningPhase = inliningPhase;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        Map<Entry, Map<InvokeNode, ResolvedJavaMethod>> toFuse = prepare(graph, context);
        inliningPhase.apply(graph, context);

        toFuse.forEach((entry, fusingMap) -> {
            Set<InvokeNode> visited = new HashSet<>();
            fusingMap.forEach((invoke, fusingMethod) -> {
                if (!visited.contains(invoke)) {
                    visited.add(invoke);
                    if (fusingMap.containsKey(invoke.predecessor()) || fusingMap.containsKey(invoke.next())) {

                        Node curr = invoke;
                        while (fusingMap.containsKey(curr.predecessor()))
                            curr = curr.predecessor();

                        // Add Fusing.stage

                        while (fusingMap.containsKey(curr)) {
                            InvokeNode i = (InvokeNode) curr;
                            i.callTarget().setTargetMethod(fusingMap.get(curr));
                            curr = i.next();
                        }

                        ResolvedJavaMethod fuseMethod = context.getMetaAccess().lookupJavaMethod(entry.getFuseMethod());

// GraphKit kit = new GraphKit(debug, thisMethod, providers, wordTypes,
// providers.getGraphBuilderPlugins(), compilationId, toString());

// CallTargetNode target = new MethodCallTargetNode(curr.getInvokeKind(), fusingMap.get(curr),
// curr.callTarget().arguments().toArray(new ValueNode[0]),
// curr.callTarget().returnStamp(),
// ((MethodCallTargetNode) curr.callTarget()).getProfile());
// InvokeNode fusingInvoke = new InvokeNode(target, BytecodeFrame.UNKNOWN_BCI);

                        // Add fusing.apply
                    }
                }
            });
            inliningPhase.apply(graph, context);
        });
    }

    private static Map<Entry, Map<InvokeNode, ResolvedJavaMethod>> prepare(StructuredGraph graph, PhaseContext context) {
        Map<Config.Entry, Map<InvokeNode, ResolvedJavaMethod>> toFuse = new HashMap<>();
        graph.getNodes().filter(InvokeNode.class).forEach(i -> {
            Config.instance.getEntries().forEach(e -> {
                e.getMethods().forEach((target, fusing) -> {
                    if (i.callTarget().targetMethod().equals(context.getMetaAccess().lookupJavaMethod(target))) {
                        ResolvedJavaMethod method = context.getMetaAccess().lookupJavaMethod(fusing);
                        toFuse.putIfAbsent(e, new HashMap<>());
                        toFuse.get(e).put(i, method);
                        i.setUseForInlining(false);
                    }
                });
            });
        });
        return toFuse;
    }
}
