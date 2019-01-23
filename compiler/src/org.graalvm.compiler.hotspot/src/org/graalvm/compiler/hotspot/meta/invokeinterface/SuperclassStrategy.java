package org.graalvm.compiler.hotspot.meta.invokeinterface;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
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
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SuperclassStrategy implements InvokeInterfaceStrategy {

    @Override
    public Optional<Evaluation> evaluate(Info info) {
        Set<Optional<HotSpotResolvedJavaMethod>> set = Arrays.stream(info.callTarget.getProfile().getTypes()).map(
                        ptype -> superClassMethod(info, ptype)).collect(Collectors.toSet());
        Set<ResolvedJavaType> superClasses = Arrays.stream(info.callTarget.getProfile().getTypes()).map(t -> t.getType().getSuperclass()).collect(Collectors.toSet());
        if (set.size() == 1 && superClasses.size() == 1) {
            return set.iterator().next().map(superClassMethod -> new Evaluation() {

                @Override
                public int effort() {
                    return 3;
                }

                @Override
                public Optional<ValueNode> apply() {
                    ResolvedJavaType superclass = superClasses.iterator().next();
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
                    return Optional.of(info.graph.unique(ConstantNode.forInt(superClassMethod.vtableEntryOffset(superclass))));
                }
            });
        } else {
            return Optional.empty();
        }
    }

    private static Optional<HotSpotResolvedJavaMethod> superClassMethod(Info info, ProfiledType ptype) {
        ResolvedJavaType superclass = ptype.getType().getSuperclass();
        HotSpotResolvedJavaMethod m = (HotSpotResolvedJavaMethod) superclass.resolveMethod(info.hsMethod, info.receiverType);
        if (m != null && m.isInVirtualMethodTable(superclass))
            return Optional.of(m);
        else
            return Optional.empty();
    }

}
