package org.graalvm.compiler.phases.common.fusing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
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
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

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

            Set<InvokeNode> roots = roots(fusingMap);

            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "before fusing " + entry.getFusingClass());

            roots.forEach(invoke -> {
                ResolvedJavaMethod stageMethod = context.getMetaAccess().lookupJavaMethod(entry.getStageMethod());
                InvokeNode stageInvoke = createInvoke(graph, stageMethod, InvokeKind.Static, invoke.callTarget().arguments().first());

                stageInvoke.setUseForInlining(true);
                stageInvoke.setStateAfter(invoke.stateAfter());
                invoke.replaceAtPredecessor(stageInvoke);
                stageInvoke.replaceFirstSuccessor(null, invoke);

                invoke.callTarget().replaceFirstInput(invoke.callTarget().arguments().first(), stageInvoke);

                InvokeNode curr = invoke;
                while (true) {
                    curr.callTarget().setTargetMethod(fusingMap.get(curr));
                    curr.setUseForInlining(true);
                    if (fusingMap.containsKey(curr.next()))
                        curr = (InvokeNode) curr.next();
                    else
                        break;
                }

                ResolvedJavaMethod fuseMethod = context.getMetaAccess().lookupJavaMethod(entry.getFuseMethod());
                InvokeNode fuseInvoke = createInvoke(graph, fuseMethod, InvokeKind.Virtual, curr);

                Node next = curr.next();
                fuseInvoke.setUseForInlining(true);
                fuseInvoke.setStateAfter(curr.stateAfter());
                next.replaceAtPredecessor(fuseInvoke);
                next.replaceFirstInput(curr, fuseInvoke);
                fuseInvoke.replaceFirstSuccessor(null, next);
            });
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "after fusing " + entry.getFusingClass());
            inliningPhase.apply(graph, context);
        });
    }

    private static Set<InvokeNode> roots(Map<InvokeNode, ResolvedJavaMethod> fusingMap) {
        Set<InvokeNode> roots = new HashSet<>();
        fusingMap.forEach((invoke, fused) -> {
            if (fusingMap.containsKey(invoke.predecessor()) || fusingMap.containsKey(invoke.next())) {
                InvokeNode curr = invoke;
                while (fusingMap.containsKey(curr.predecessor())) {
                    curr = (InvokeNode) curr.predecessor();
                }
                roots.add(curr);
            }
        });
        return roots;
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

    private static InvokeNode createInvoke(StructuredGraph graph, ResolvedJavaMethod method, InvokeKind invokeKind, ValueNode... args) {
        try (DebugCloseable context = graph.withNodeSourcePosition(NodeSourcePosition.substitution(graph.currentNodeSourcePosition(), method))) {
            assert method.isStatic() == (invokeKind == InvokeKind.Static);
            Signature signature = method.getSignature();
            JavaType returnType = signature.getReturnType(null);
            StampPair returnStamp = StampFactory.forDeclaredType(graph.getAssumptions(), returnType, false);
            MethodCallTargetNode callTarget = graph.add(new MethodCallTargetNode(invokeKind, method, args, returnStamp, null));
            InvokeNode invoke = graph.addOrUniqueWithInputs(new InvokeNode(callTarget, BytecodeFrame.UNKNOWN_BCI));
            return invoke;
        }
    }
}
