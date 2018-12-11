package org.graalvm.compiler.hotspot.meta.invoke;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
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
        public final LoweringTool loweringTool;
        public final ValueNode receiver;
        public final MetaAccessProvider metaAccess;

        public Info(StructuredGraph graph, ValueNode hub, Invoke invoke, GraalHotSpotVMConfig config, TargetDescription target, HotSpotConstantReflectionProvider constantReflection,
                        LoweringTool loweringTool, ValueNode receiver, MetaAccessProvider metaAccess) {
            this.graph = graph;
            this.hub = hub;
            this.invoke = invoke;
            this.config = config;
            this.target = target;
            this.constantReflection = constantReflection;
            this.loweringTool = loweringTool;
            this.receiver = receiver;
            this.metaAccess = metaAccess;
            this.callTarget = (MethodCallTargetNode) invoke.callTarget();
            this.hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
            this.receiverType = invoke.getReceiverType();
        }
    }

    public static abstract class Evaluation {
        public abstract NodeCycles cycles();

        public abstract Optional<ValueNode> apply();

        @Override
        public String toString() {
            return "(" + cycles().value + ") " + this.getClass().getName().replace("org.graalvm.compiler.hotspot.meta.invoke.", "");
        }
    }

    public Optional<Evaluation> evaluate(Info info);

    public static Optional<ValueNode> resolve(StructuredGraph graph, ValueNode hub, Invoke invoke, GraalHotSpotVMConfig config, TargetDescription target,
                    HotSpotConstantReflectionProvider constantReflection, LoweringTool loweringTool, ValueNode receiver, MetaAccessProvider metaAccess) {

        Info info = new Info(graph, hub, invoke, config, target, constantReflection, loweringTool, receiver, metaAccess);

        String strategiesOption = GraalOptions.MethodOffsetStrategies.getValue(graph.getOptions());

        if (strategiesOption == null)
            throw new NullPointerException("MethodOffsetStrategies option can't be null");

        List<MethodOffsetStrategy> strategies = new ArrayList<>();
        for (String str : strategiesOption.split(",")) {
            if ("FixedOffset".equals(str))
                strategies.add(new FixedOffsetStrategy());
            else if ("Superclass".equals(str))
                strategies.add(new SuperclassStrategy());
            else if ("Caching".equals(str))
                strategies.add(new CachingStrategy());
            else if ("Fallback".equals(str))
                strategies.add(new FallbackStrategy());
            else if (!str.isEmpty())
                throw new IllegalStateException("Invalid method offset strategy: " + str);
        }

        List<Evaluation> evaluations = strategies.stream().flatMap(s -> s.evaluate(info).map(Stream::of).orElseGet(Stream::empty)).sorted((a, b) -> a.cycles().value - b.cycles().value).collect(
                        Collectors.toList());

        if (evaluations.isEmpty()) {
            return Optional.empty();
        } else {
            Evaluation evaluation = evaluations.get(0);
            if (!evaluation.getClass().getName().contains("Fixed") &&
                            !strategies.get(0).getClass().getName().contains("Fallback"))
                System.out.println("" + graph.method().getName() + ": " + invoke + " => " + evaluations);
            Optional<ValueNode> value = evaluation.apply();
            return value;
        }
    }
}
