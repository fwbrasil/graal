package org.graalvm.compiler.phases.common.fusion;

import java.lang.reflect.Method;
import java.util.Arrays;
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

            Map<Method, Method> fusionMap = fusionMap();
            Map<InvokeNode, ResolvedJavaMethod> toFuse = new HashMap<>();
            graph.getNodes().filter(InvokeNode.class).forEach(invoke -> {
                for (Map.Entry<Method, Method> e : fusionMap.entrySet()) {
                    ResolvedJavaMethod fusedMethod = resolve(e.getKey());
                    ResolvedJavaMethod invokeMethod = invoke.callTarget().targetMethod();
                    if (fusedMethod.getSignature().getReturnKind().equals(invokeMethod.getSignature().getReturnKind()) && fusedMethod.getName().equals(invokeMethod.getName()) &&
                                    Arrays.equals(fusedMethod.getParameters(), invokeMethod.getParameters()))
                        toFuse.put(invoke, resolve(e.getValue()));
                }
            });

            Set<InvokeNode> roots = roots(toFuse);
            for (InvokeNode root : roots) {
                InvokeNode curr = root;
                while (toFuse.containsKey(curr.next())) {
                    curr.callTarget().setTargetMethod(toFuse.get(curr));
                    curr = (InvokeNode) curr.next();
                }
            }

            dump("after fusion");
        }

        private ResolvedJavaMethod resolve(Method m) {
            return context.getMetaAccess().lookupJavaMethod(m);
        }

        private void dump(String format, Object... args) {
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, String.format(format, args));
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

        private Map<Method, Method> fusionMap() {
            String fuseClass = System.getProperty("FuseClass");
            if (fuseClass == null)
                return new HashMap<>();
            Class<?> cls;
            try {
                cls = ClassLoader.getSystemClassLoader().loadClass(fuseClass);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            Map<String, Method> byName = new HashMap<>();
            for (Method m : cls.getMethods())
                byName.put(m.getName(), m);

            Map<Method, Method> toFuse = new HashMap<>();
            for (Method m : cls.getMethods()) {
                String fusedName = "fused" + StringUtils.capitalize(m.getName());
                if (byName.containsKey(fusedName)) {
                    Method fusedMethod = byName.get(fusedName);
                    if (fusedMethod.getReturnType().equals(m.getReturnType()) &&
                                    Arrays.equals(m.getParameterTypes(), fusedMethod.getParameterTypes()))
                        toFuse.put(m, fusedMethod);
                }
            }
            return toFuse;
        }
    }
}