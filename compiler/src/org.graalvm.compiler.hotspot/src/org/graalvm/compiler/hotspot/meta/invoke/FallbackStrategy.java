package org.graalvm.compiler.hotspot.meta.invoke;

import java.util.Optional;

import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaType;

public class FallbackStrategy implements MethodOffsetStrategy {

    @Override
    public Optional<Evaluation> evaluate(Info info) {
        return Optional.of(new Evaluation() {

            @Override
            public int effort() {
                ResolvedJavaType type = info.callTarget.targetMethod().getDeclaringClass();
                if (!type.isInterface()) {
                    return 0;
                } else {
                    ProfiledType[] ptypes = info.callTarget.getProfile().getTypes();
                    double effort = 0D;
                    for (ProfiledType ptype : ptypes) {
                        ResolvedJavaType[] interfaces = ptype.getType().getInterfaces();
                        int i = 0;
                        while (i < interfaces.length && interfaces[i] != type) {
                            i++;
                        }
                        assert interfaces[i] == type;
                        effort += ptype.getProbability() * (i + 1) * 2;
                    }
                    return (int) effort;
                }
            }

            @Override
            public Optional<ValueNode> apply() {
                return Optional.empty();
            }
        });
    }

}
