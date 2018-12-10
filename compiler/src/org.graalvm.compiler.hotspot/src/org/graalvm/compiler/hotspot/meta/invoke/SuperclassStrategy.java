package org.graalvm.compiler.hotspot.meta.invoke;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.memory.HeapAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SuperclassStrategy implements MethodOffsetStrategy {

    @Override
    public Optional<Evaluation> evaluate(Info info) {
        ResolvedJavaType type = info.callTarget.targetMethod().getDeclaringClass();

        if (!type.isInterface()) {
            return Optional.empty();
        } else {
            Set<Optional<HotSpotResolvedJavaMethod>> set = Arrays.stream(info.callTarget.getProfile().getTypes()).map(
                            ptype -> Optional.ofNullable((HotSpotResolvedJavaMethod) ptype.getType().getSuperclass().findMethod(info.hsMethod.getName(), info.hsMethod.getSignature()))).collect(
                                            Collectors.toSet());
            if (set.size() == 1) {
                return set.iterator().next().filter(m -> m.isInVirtualMethodTable(m.getDeclaringClass())).map(superClassMethod -> new Evaluation() {

                    @Override
                    public int effort() {
                        return 3;
                    }

                    @Override
                    public Optional<ValueNode> apply() {
                        HotSpotResolvedObjectType declaringClass = superClassMethod.getDeclaringClass();

                        ConstantNode expectedSuperClass = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), declaringClass.klass(), info.metaAccess,
                                        info.graph);

                        ValueNode o = ConstantNode.forIntegerKind(info.target.wordJavaKind, info.config.klassSuperKlassOffset, info.graph);
                        OffsetAddressNode superClassAddress = info.graph.unique(new OffsetAddressNode(info.hub, o));
                        ReadNode receiverSuperClass = info.graph.add(
                                        new ReadNode(superClassAddress, LocationIdentity.any(), KlassPointerStamp.klassNonNull(), HeapAccess.BarrierType.NONE));

                        LogicNode check = CompareNode.createCompareNode(info.graph, CanonicalCondition.EQ, expectedSuperClass, receiverSuperClass, info.constantReflection, NodeView.DEFAULT);
                        FixedGuardNode guard = info.graph.add(new FixedGuardNode(check, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile));

                        info.graph.addBeforeFixed(info.invoke.asNode(), receiverSuperClass);
                        info.graph.addBeforeFixed(info.invoke.asNode(), guard);
                        return Optional.of(info.graph.unique(ConstantNode.forInt(superClassMethod.vtableEntryOffset(declaringClass))));
                    }
                });
            } else {
                return Optional.empty();
            }
        }
    }

}
