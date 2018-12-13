package org.graalvm.compiler.hotspot.meta.invoke;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;

public class SingleMethodStrategy implements MethodOffsetStrategy {

    @Override
    public Optional<Evaluation> evaluate(Info info) {
        JavaTypeProfile profile = info.callTarget.getProfile();
        ProfiledType[] ptypes = profile.getTypes();
        int objectVtableLength = ((HotSpotResolvedObjectType) info.metaAccess.lookupJavaType(Object.class)).getVtableLength();

        boolean allSingleMethod = Arrays.stream(ptypes).allMatch(ptype -> ((HotSpotResolvedObjectType) ptype.getType()).getVtableLength() == objectVtableLength + 1);
        Map<Integer, List<ProfiledType>> offsetMap = MethodOffsetStrategy.offsetMap(info, ptypes);

        if (allSingleMethod && offsetMap != null && offsetMap.size() == 1 && ptypes.length > 0 && profile.getNotRecordedProbability() == 0D) {

            System.out.println("SingleMethod " + offsetMap + " " + Arrays.asList(ptypes));
            return Optional.empty();
// return Optional.of(new Evaluation() {
//
// @Override
// public int effort() {
// return 0;
// }
//
// @Override
// public Optional<ValueNode> apply() {
// return null;
// }
//
// });
        } else {
            return Optional.empty();
        }
    }

}
