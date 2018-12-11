package org.graalvm.compiler.hotspot.meta.invoke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.java.TypeSwitchNode;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CachingStrategy implements MethodOffsetStrategy {

    @Override
    public Optional<Evaluation> evaluate(Info info) {
        JavaTypeProfile profile = info.callTarget.getProfile();
        double notRecordedProbability = profile.getNotRecordedProbability();
        ProfiledType[] ptypes = profile.getTypes();
        Map<Integer, List<ProfiledType>> offsets = new HashMap<>();

        for (ProfiledType ptype : ptypes) {
            ResolvedJavaType type = ptype.getType();
            HotSpotResolvedJavaMethod concrete = (HotSpotResolvedJavaMethod) type.resolveConcreteMethod(info.callTarget.targetMethod(), info.invoke.getContextType());
            if (!concrete.isInVirtualMethodTable(type))
                return Optional.empty();
            int offset = concrete.vtableEntryOffset(type);
            offsets.computeIfAbsent(offset, k -> new ArrayList<>()).add(ptype);
        }

        ResolvedJavaType[] keys = new ResolvedJavaType[ptypes.length];
        double[] keyProbabilities = new double[ptypes.length + 1];
        int[] keySuccessors = new int[ptypes.length + 1];
        double totalProbability = notRecordedProbability;

        int successorIdx = 0;
        int keyIdx = 0;
        for (Map.Entry<Integer, List<ProfiledType>> entry : offsets.entrySet()) {
            for (ProfiledType ptype : entry.getValue()) {
                keys[keyIdx] = ptype.getType();
                keySuccessors[keyIdx] = successorIdx;
                double probability = ptype.getProbability();
                keyProbabilities[keyIdx] = probability;
                totalProbability += probability;
                keyIdx++;
            }
            successorIdx++;
        }

        keyProbabilities[keyProbabilities.length - 1] = notRecordedProbability;
        keySuccessors[keySuccessors.length - 1] = offsets.size();

        for (int i = 0; i < keyProbabilities.length; i++) {
            keyProbabilities[i] /= totalProbability;
        }

        return Optional.of(new Evaluation() {

            @Override
            public NodeCycles cycles() {
                return NodeCycles.compute(keys.length * 2);
            }

            @Override
            public Optional<ValueNode> apply() {
                AbstractBeginNode[] successors = new AbstractBeginNode[offsets.size() + 1];
                MergeNode merge = info.graph.add(new MergeNode());
                ValuePhiNode phi = info.graph.addOrUnique(new ValuePhiNode(StampFactory.intValue(), merge));

                int i = 0;
                for (Map.Entry<Integer, List<ProfiledType>> entry : offsets.entrySet()) {
                    BeginNode begin = info.graph.add(new BeginNode());
                    successors[i] = begin;
                    EndNode end = info.graph.add(new EndNode());
                    begin.setNext(end);
                    merge.addForwardEnd(end);
                    phi.initializeValueAt(i, ConstantNode.forIntegerKind(info.target.wordJavaKind, entry.getKey(), info.graph));
                    i++;
                }

                successors[i] = BeginNode.begin(info.graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TypeCheckedInliningViolated)));

                TypeSwitchNode typeSwitch = info.graph.add(new TypeSwitchNode(info.hub, successors, keys, keyProbabilities, keySuccessors, info.constantReflection));
                FixedWithNextNode pred = (FixedWithNextNode) info.invoke.asNode().predecessor();
                pred.setNext(typeSwitch);
                merge.setNext(info.invoke.asNode());

                return Optional.of(phi);
            }
        });
    }
}
