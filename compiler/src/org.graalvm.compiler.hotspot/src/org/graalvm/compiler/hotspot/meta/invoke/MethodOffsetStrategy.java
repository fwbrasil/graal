package org.graalvm.compiler.hotspot.meta.invoke;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface MethodOffsetStrategy {

    public static class Info {
        public final StructuredGraph graph;
        public final ValueNode hub;
        public final Invoke invoke;
        public final GraalHotSpotVMConfig config;
        public final TargetDescription target;
        public final HotSpotConstantReflectionProvider constantReflection;
        public final MethodCallTargetNode callTarget;
        public final HotSpotResolvedJavaMethod hsMethod;
        public final ResolvedJavaType receiverType;

        public Info(StructuredGraph graph, ValueNode hub, Invoke invoke, GraalHotSpotVMConfig config, TargetDescription target, HotSpotConstantReflectionProvider constantReflection) {
            this.graph = graph;
            this.hub = hub;
            this.invoke = invoke;
            this.config = config;
            this.target = target;
            this.constantReflection = constantReflection;
            this.callTarget = (MethodCallTargetNode) invoke.callTarget();
            this.hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
            this.receiverType = invoke.getReceiverType();
        }
    }

    public static interface Evaluation {
        public NodeCycles cycles();

        public ValueNode apply();
    }

    public Optional<Evaluation> evaluate(Info info);

    static final List<MethodOffsetStrategy> strategies = Arrays.asList(new FixedVTableOffsetStrategy(), new CachingOffsetStrategy());

    public static Optional<ValueNode> resolve(StructuredGraph graph, ValueNode hub, Invoke invoke, GraalHotSpotVMConfig config, TargetDescription target,
                    HotSpotConstantReflectionProvider constantReflection) {

        Info info = new Info(graph, hub, invoke, config, target, constantReflection);
        return strategies.stream().flatMap(s -> s.evaluate(info).map(Stream::of).orElseGet(Stream::empty)).sorted((a, b) -> b.cycles().value - a.cycles().value).findFirst().map(
                        Evaluation::apply);

// return Optional.empty();
    }
}
