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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.EntryMarkerNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.LockEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.fusion.MethodFusionPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ExperimentationTest extends GraalCompilerTest {

    public static List<Integer> notFused(List<Integer> l) {
        return l.map(i -> i + 1).map(i -> i + 2);
    }

    public static List<Integer> fused(List<Integer> l, Function<Integer, Integer> f1, Function<Integer, Integer> f2) {
        return ListFusion.stage(l).map(f1).map(f2).fuse();
    }

    public static List<Integer> fused2(List<Integer> l) {
        return l.map(i -> i + 1 + 2);
    }

    public static List<Integer> pattern(List<Integer> l, Function<Integer, Integer> f1, Function<Integer, Integer> f2) {
        return secondMap(l.map(f1), f2);
    }

    public static List<Integer> secondMap(List<Integer> l, Function<Integer, Integer> f2) {
        return l.map(f2);
    }

    public static List<Integer> optimized(List<Integer> l, Function<Integer, Integer> f1, Function<Integer, Integer> f2) {
        return l.map(v -> f2.apply(f1.apply(v)));
    }

    public static boolean a = false;

    public static List<Integer> sideEffect(List<Integer> l, Function<Integer, Integer> f1, Function<Integer, Integer> f2) {
        return l.map(i -> {
            if (a)
                return f1.apply(i);
            else
                return i + 1;
        });
    }

    public static boolean stringEquals(String a, String b) {
        return sub(a, b + 1);
    }

    public static boolean sub(String a, String b) {
        return a.equals(b);
    }

    public static boolean atomic() {
        AtomicInteger a = new AtomicInteger(0);
        return a.compareAndSet(0, 1);
    }

    @Test
    public void test() {

        System.setProperty("fusion",
                        "org.graalvm.compiler.core.test.fusion.List:org.graalvm.compiler.core.test.fusion.ListFusion");

        ListFusion.stage(new List(1, 2));

        Function<Integer, Integer> f1 = v -> v + 1;
        Function<Integer, Integer> f2 = v -> v + 2;
        test("pattern", new List<>(1, 2, 3), f1, f2);

// test("stringEquals", "aa", "bb");

// List<Integer> l = new List<Integer>(1, 2, 3);
//
// for (int i = 0; i < 10000; i++) {
// l = pattern(l, v -> v + 1, v -> v + 2);
// }
//
// System.out.println(l);
//
// List<Integer> l2 = new List<Integer>(1, 2, 3);
//
// for (int i = 0; i < 10000; i++) {
// l2 = notFused(l2);
// }
//
// System.out.println(l2);
//
// StructuredGraph notFused = getGraph("pattern");
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

            MethodFusionPhase fusingPhase = new MethodFusionPhase(inliningPhase);

            fusingPhase.apply(graph, context);

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
