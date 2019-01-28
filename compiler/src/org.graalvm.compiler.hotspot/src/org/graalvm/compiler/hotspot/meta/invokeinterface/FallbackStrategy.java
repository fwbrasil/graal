package org.graalvm.compiler.hotspot.meta.invokeinterface;

import java.util.Optional;

import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaType;

public class FallbackStrategy implements InvokeInterfaceStrategy {

    @Override
    public Optional<Evaluation> evaluate(Info info) {
        return Optional.of(new Evaluation() {

            @Override
            public int effort() {
                ResolvedJavaType type = info.callTarget.targetMethod().getDeclaringClass();
                ProfiledType[] ptypes = info.callTarget.getProfile().getTypes();
                double effort = 0D;
                for (ProfiledType ptype : ptypes) {
                    ResolvedJavaType[] interfaces = ptype.getType().getInterfaces();
                    int i = 1;
                    while (i < interfaces.length && interfaces[i] != type) {
                        i++;
                    }
                    assert interfaces[i] == type;
                    effort += ptype.getProbability() * i;
                }
                return (int) effort + 1;
            }

            @Override
            public Optional<ValueNode> apply() {
                return Optional.empty();
            }
        });
    }

}
