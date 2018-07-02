package org.graalvm.compiler.phases.common.fusion;

import java.lang.reflect.Method;
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
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.NodeWithState;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.fusion.Config.Entry;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

public class MethodFusionPhase extends BasePhase<HighTierContext> {

    private final InliningPhase inliningPhase;

    public MethodFusionPhase(InliningPhase inliningPhase) {
        this.inliningPhase = inliningPhase;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        new Instance(graph, context).run();
    }

    private class Instance {
        private final StructuredGraph graph;
        private final HighTierContext context;
        private final FrameStateBuilder frameState;
        private final ResolvedJavaMethod outerMethod;

        public Instance(StructuredGraph graph, HighTierContext context) {
            this.graph = graph;
            this.context = context;
            this.outerMethod = graph.method();
            this.frameState = new FrameStateBuilder(outerMethod, graph);
        }

        public void run() {
            Map<Entry, Map<InvokeNode, ResolvedJavaMethod>> toFuse = prepare();

            inliningPhase.apply(graph, context);

            toFuse.forEach((entry, fusionMap) -> {

                Set<InvokeNode> roots = roots(fusionMap);

                dump("before fusion %s", entry.getFusionClass());

                roots.forEach(invoke -> {

                    FrameState state = previousState(invoke.predecessor());

                    InvokeNode stageInvoke = createInvoke(entry.getStageMethod(), InvokeKind.Static, invoke.callTarget().arguments().first());

                    invoke.replaceAtPredecessor(stageInvoke);
                    stageInvoke.replaceFirstSuccessor(null, invoke);
                    stageInvoke.setStateAfter(state);

                    invoke.callTarget().replaceFirstInput(invoke.callTarget().arguments().first(), stageInvoke);

                    InvokeNode curr = invoke;
                    while (true) {

                        FixedNode next = curr.next();
                        InvokeNode newInvoke = createInvoke(fusionMap.get(curr), InvokeKind.Virtual, curr.callTarget().arguments().first());

                        curr.callTarget().replaceAndDelete(newInvoke.callTarget());
                        graph.replaceFixedWithFixed(curr, newInvoke);
                        newInvoke.setStateAfter(state);

                        curr = newInvoke;
                        if (fusionMap.containsKey(next))
                            curr = (InvokeNode) next;
                        else
                            break;
                    }

                    InvokeNode fuseInvoke = createInvoke(entry.getFuseMethod(), InvokeKind.Virtual, curr);

                    Node next = curr.next();

                    next.replaceAtPredecessor(fuseInvoke);
                    next.replaceFirstInput(curr, fuseInvoke);
                    fuseInvoke.replaceFirstSuccessor(null, next);
                });

                dump("after fusion %s", entry.getFusionClass());

                inliningPhase.apply(graph, context);

                dump("after inlinig %s", entry.getFusionClass());
            });
        }

        private FrameState previousState(Node node) {
            while (!(node instanceof NodeWithState)) // TODO nulls
                node = node.predecessor();
            return ((NodeWithState) node).states().first();
        }

        private void dump(String format, Object... args) {
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, String.format(format, args));
        }

        private void setFrameState(ResolvedJavaMethod method, InvokeNode stageInvoke) {
            FrameStateBuilder frameStateBuilder = new FrameStateBuilder(method, graph);
            if (stageInvoke.getStackKind() != JavaKind.Void) {
                frameStateBuilder.push(stageInvoke.getStackKind(), stageInvoke);
            }
            stageInvoke.setStateAfter(frameStateBuilder.create(BytecodeFrame.BEFORE_BCI, stageInvoke));
            if (stageInvoke.getStackKind() != JavaKind.Void) {
                frameStateBuilder.pop(stageInvoke.getStackKind());
            }
        }

        private InvokeNode createInvoke(Method method, InvokeKind invokeKind, ValueNode... args) {
            return createInvoke(context.getMetaAccess().lookupJavaMethod(method), invokeKind, args);
        }

        private InvokeNode createInvoke(ResolvedJavaMethod method, InvokeKind invokeKind, ValueNode... args) {
            try (DebugCloseable context = graph.withNodeSourcePosition(NodeSourcePosition.substitution(graph.currentNodeSourcePosition(), method))) {
                assert method.isStatic() == (invokeKind == InvokeKind.Static);
                Signature signature = method.getSignature();
                JavaType returnType = signature.getReturnType(null);
                StampPair returnStamp = StampFactory.forDeclaredType(graph.getAssumptions(), returnType, false);
                MethodCallTargetNode callTarget = graph.add(new MethodCallTargetNode(invokeKind, method, args, returnStamp, null));
                InvokeNode invoke = graph.addOrUniqueWithInputs(new InvokeNode(callTarget, BytecodeFrame.BEFORE_BCI));
                return invoke;
            }
        }

        private Map<Entry, Map<InvokeNode, ResolvedJavaMethod>> prepare() {
            Map<Config.Entry, Map<InvokeNode, ResolvedJavaMethod>> toFuse = new HashMap<>();
            graph.getNodes().filter(InvokeNode.class).forEach(i -> {
                Config.instance.getEntries().forEach(e -> {
                    e.getMethods().forEach((target, fusion) -> {
                        if (i.callTarget().targetMethod().equals(context.getMetaAccess().lookupJavaMethod(target))) {
                            ResolvedJavaMethod method = context.getMetaAccess().lookupJavaMethod(fusion);
                            toFuse.putIfAbsent(e, new HashMap<>());
                            toFuse.get(e).put(i, method);
                            i.setUseForInlining(false);
                        }
                    });
                });
            });
            return toFuse;
        }

        private Set<InvokeNode> roots(Map<InvokeNode, ResolvedJavaMethod> fusionMap) {
            Set<InvokeNode> roots = new HashSet<>();
            fusionMap.forEach((invoke, fused) -> {
                if (fusionMap.containsKey(invoke.predecessor()) || fusionMap.containsKey(invoke.next())) {
                    InvokeNode curr = invoke;
                    while (fusionMap.containsKey(curr.predecessor())) {
                        curr = (InvokeNode) curr.predecessor();
                    }
                    roots.add(curr);
                }
            });
            return roots;
        }
    }
}
