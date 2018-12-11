package org.graalvm.compiler.hotspot.meta.invoke;

import java.util.Optional;

import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;

public class FixedVTableOffsetStrategy implements MethodOffsetStrategy {

    @Override
    public Optional<Evaluation> evaluate(Info info) {
        if (info.hsMethod.isInVirtualMethodTable(info.receiverType)) {
            return Optional.of(new Evaluation() {

                @Override
                public NodeCycles cycles() {
                    return NodeCycles.CYCLES_0;
                }

                @Override
                public ValueNode apply() {
                    return ConstantNode.forIntegerKind(info.target.wordJavaKind, info.hsMethod.vtableEntryOffset(info.receiverType), info.graph);
                }
            });
        } else {
            return Optional.empty();
        }
    }
}
