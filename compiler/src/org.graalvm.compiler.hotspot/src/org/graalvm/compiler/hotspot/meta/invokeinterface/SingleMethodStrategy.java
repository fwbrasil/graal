package org.graalvm.compiler.hotspot.meta.invokeinterface;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;

public class SingleMethodStrategy implements InvokeInterfaceStrategy {

    @Override
    public Optional<Evaluation> evaluate(Info info) {
        JavaTypeProfile profile = info.callTarget.getProfile();
        ProfiledType[] ptypes = profile.getTypes();
        int objectVtableLength = ((HotSpotResolvedObjectType) info.metaAccess.lookupJavaType(Object.class)).getVtableLength();

        boolean allSingleMethod = Arrays.stream(ptypes).allMatch(ptype -> ((HotSpotResolvedObjectType) ptype.getType()).getVtableLength() == objectVtableLength + 1);
        if (allSingleMethod)
            System.out.println(Arrays.asList(ptypes));

        if (allSingleMethod) {
            return Optional.of(new Evaluation() {

                @Override
                public int effort() {
                    return 0;
                }

                @Override
                public Optional<ValueNode> apply() {
                    return Optional.empty();
                }

            });
        } else {
            return Optional.empty();
        }
    }

}
