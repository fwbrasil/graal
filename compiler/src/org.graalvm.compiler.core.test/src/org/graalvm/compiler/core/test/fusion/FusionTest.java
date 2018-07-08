/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.core.test.fusion;

import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class FusionTest extends GraalCompilerTest {

    public static List<Integer> example(List<Integer> l, Function<Integer, Integer> f1, Function<Integer, Integer> f2) {
        return l.map(f1).map(f2);
    }

    @Test
    public void test() {

        System.setProperty("FuseClass", "org.graalvm.compiler.core.test.fusion.List");

        Function<Integer, Integer> f1 = v -> v + 1;
        Function<Integer, Integer> f2 = v -> v + 2;

        List.apply(1, 2, 3).fusedMap(f1);

// time("fused", () -> {
// List<Integer> l = List.apply(1, 2, 3);
// for (int i = 0; i < 10_000; i++)
// l = example(List.apply(1, 2, 3), f1, f2);
// return l;
// });

        test("example", List.apply(1, 2, 3), f1, f2);
    }

    private <T> void time(String label, Supplier<T> s) {
        for (int i = 0; i < 10_000; i++) {
            long start = System.nanoTime();
            T result = s.get();
            System.out.println(label + ": " + ((System.nanoTime() - start) / 1000));
        }
    }

    private StructuredGraph getGraph(String snippet) {
        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        StructuredGraph graph = parseEager(method, AllowAssumptions.YES);

        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("Test")) {
            HighTierContext context = getDefaultHighTierContext();
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "after parsing");
            new CanonicalizerPhase().apply(graph, context);
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "after canonicalizer ");

            InliningPhase inliningPhase = new InliningPhase(new CanonicalizerPhase());

// MethodFusionPhase fusingPhase = new MethodFusionPhase(inliningPhase);

// fusingPhase.apply(graph, context);

            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "after fusing ");

// new CanonicalizerPhase().apply(graph, context);
// new DeadCodeEliminationPhase().apply(graph);
// new LoweringPhase(new CanonicalizerPhase(),
// LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
// new LockEliminationPhase().apply(graph);
            return graph;
        }
    }

}