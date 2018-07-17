package org.graalvm.compiler.phases.common.fusion;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.graph.iterators.NodePredicates;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.NodeWithState;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import com.sun.xml.internal.ws.util.StringUtils;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

public class MethodFusionPhase extends BasePhase<HighTierContext> {

    private final InliningPhase inliningPhase;

    public MethodFusionPhase(InliningPhase inliningPhase) {
        super();
        this.inliningPhase = inliningPhase;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        new Instance(graph, context).run();
    }

    private class Instance {
        private final StructuredGraph graph;
        private final HighTierContext context;

        public Instance(StructuredGraph graph, HighTierContext context) {
            this.graph = graph;
            this.context = context;
        }

        public void run() {

            dump("before fusion");

            inliningPhase.apply(graph, context);
            dump("after fusion partial inlining");

            Map<Method, Method> fusionMap = MethodFusionConfig.fusionMap;
            Map<InvokeNode, ResolvedJavaMethod> toFuse = new HashMap<>();
            graph.getNodes().filter(InvokeNode.class).forEach(invoke -> {
                for (Map.Entry<Method, Method> e : fusionMap.entrySet()) {
                    ResolvedJavaMethod fusedMethod = resolve(e.getKey());
                    ResolvedJavaMethod invokeMethod = invoke.callTarget().targetMethod();
                    if (fusedMethod.getSignature().getReturnKind().equals(invokeMethod.getSignature().getReturnKind()) && fusedMethod.getName().equals(invokeMethod.getName()) &&
                                    Arrays.equals(fusedMethod.getParameters(), invokeMethod.getParameters())) {
                        toFuse.put(invoke, resolve(e.getValue()));
                    }
                }
            });

            Set<InvokeNode> leafs = leafs(toFuse);
            String log = "";
            int fusions = 0;
            for (InvokeNode leaf : leafs) {
                Node curr = leaf.callTarget().arguments().first();
                while (toFuse.containsKey(curr)) {
                    InvokeNode i = (InvokeNode) curr;
                    i.callTarget().setTargetMethod(toFuse.get(curr));
                    fusions++;
                    log += "\n   " + i.getNodeSourcePosition().getMethod() + ": " + toFuse.get(curr);
                    curr = i.callTarget().arguments().first();
                }
            }

            if (!log.isEmpty())
                System.out.println("-------- " + fusions + " fusions: " + graph.asJavaMethod().toString() + log);

            dump("after fusion redirect");

            MethodFusionConfig.withInlineAll(() -> inliningPhase.apply(graph, context));
            dump("after fusion full inlining");
        }

        private ResolvedJavaMethod resolve(Method m) {
            return context.getMetaAccess().lookupJavaMethod(m);
        }

        private void dump(String format, Object... args) {
            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, String.format(format, args));
        }

        private Set<InvokeNode> leafs(Map<InvokeNode, ResolvedJavaMethod> fusionMap) {
            Set<InvokeNode> leafs = new HashSet<>();
            leafs.addAll(fusionMap.keySet());
            fusionMap.forEach((invoke, fused) -> {
                leafs.remove(invoke.callTarget().arguments().first());
            });
            return leafs;
        }

    }
}