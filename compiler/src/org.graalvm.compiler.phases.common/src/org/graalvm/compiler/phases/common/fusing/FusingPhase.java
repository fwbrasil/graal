package org.graalvm.compiler.phases.common.fusing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.fusing.Config.Entry;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.code.BytecodeFrame;
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
                    if (!fusingMap.containsKey(invoke.predecessor()) & !fusingMap.containsKey(invoke.next()))
                        visited.add(invoke);
                    else {
                        // Merge predecessors
                        Node curr = invoke;
                        while (fusingMap.containsKey(curr)) {
                            InvokeNode original = (InvokeNode) curr;
                            CallTargetNode target = new MethodCallTargetNode(original.getInvokeKind(), fusingMap.get(original), original.callTarget().arguments().toArray(new ValueNode[0]),
                                            original.callTarget().returnStamp(),
                                            ((MethodCallTargetNode) original.callTarget()).getProfile());
                            InvokeNode fusingInvoke = new InvokeNode(target, BytecodeFrame.UNKNOWN_BCI);
                            graph.replaceFixed(original, fusingInvoke);
                            curr = curr.predecessor();
                        }
                        // Add fusing class creation
// curr.replaceAtPredecessor(other);
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
                        toFuse.putIfAbsent(e, new HashMap<>()).put(i, context.getMetaAccess().lookupJavaMethod(fusing));
                        i.setUseForInlining(false);
                    }
                });
            });
        });
        return toFuse;
    }

// private static boolean shouldOptimize(InvokeNode node) {
// String name = node.getTargetMethod().getDeclaringClass().getName();
// return node.getTargetMethod().getName().equals("map") &&
// name.startsWith("Lorg/graalvm/compiler/phases/common/fusing/List");
// }
//
// private static Optional<InvokeNode> toFuse(Node node) {
// if (node instanceof InvokeNode && shouldOptimize((InvokeNode) node))
// return Optional.of((InvokeNode) node);
// else
// return Optional.empty();
// }
//
// @Override
// protected void run(StructuredGraph graph) {
// Set<InvokeNode> roots = new HashSet<>();
// graph.getNodes().filter(InvokeNode.class).forEach(n -> {
// if (shouldOptimize(n)) {
// toFuse(n.predecessor()).ifPresent(p -> {
// roots.remove(n);
// roots.add(p);
// });
// }
// });
// for (InvokeNode n : roots) {
// MethodCallTargetNode original = (MethodCallTargetNode) n.callTarget();
//// ListFusing.class.get
//// CallTargetNode target = new MethodCallTargetNode(n.getInvokeKind(), method, new ValueNode[0],
//// original.returnStamp() ,original.getProfile())
// InvokeNode stage = new InvokeNode(null, BytecodeFrame.UNKNOWN_BCI);
// n.replaceAtPredecessor(stage);
// }
// }
}
