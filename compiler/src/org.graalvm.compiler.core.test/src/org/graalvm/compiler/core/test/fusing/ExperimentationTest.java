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
package org.graalvm.compiler.core.test.fusing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
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
import org.graalvm.compiler.phases.common.fusing.FusingPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Optimize {
    public Class<?> value();
}

class List<T> {
    T[] values;

    public List(T... values) {
        this.values = values;
    }

    public int size() {
        return values.length;
    }

    public <U> List<U> map(Function<T, U> f) {
        U[] n = (U[]) new Object[values.length];
        for (int i = 0; i < values.length; i++)
            n[i] = f.apply(values[i]);
        return new List(n);
    }
}

class ListFusing {
    public static <T> ListStage<T, T> stage() {
        return new ListStage.Id();
    }
}

interface Stage<T, U> {

    U apply(T v);
}

abstract class ListStage<T, U> implements Stage<List<T>, List<U>> {

    public <V> ListStage<T, V> map(Function<U, V> f) {
        return new Map(this, f);
    }

    static class Id<T> extends ListStage<T, T> {
        @Override
        public List<T> apply(List<T> v) {
            return v;
        }
    }

    private static class Map<T, U, V> extends ListStage<T, V> {
        private final ListStage<T, U> chain;
        private final Function<U, V> func;

        public Map(ListStage<T, U> chain, Function<U, V> func) {
            super();
            this.chain = chain;
            this.func = func;
        }

        @Override
        public List<V> apply(List<T> v) {
            return chain.apply(v).map(func);
        }

        @Override
        public <X> ListStage<T, X> map(Function<V, X> f) {
            return new Map(chain, func.andThen(f));
        }
    }
}

public class ExperimentationTest extends GraalCompilerTest {

    public static List<Integer> notFused(List<Integer> l) {
        return l.map(i -> i + 1).map(i -> i + 2);
    }

    public static List<Integer> fused(List<Integer> l) {
        return l.map(i -> i + 1 + 2);
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

    public static List<Integer> fff(List<Integer> l) {
        return (new ListStage.Id<Integer>()).map(v -> v + 1).map(v -> v + 2).apply(l);
    }

    @Test
    public void test() {

        List<Integer> l = new List<Integer>(1, 2, 3);

        for (int i = 0; i < 10000; i++) {
            l = pattern(l, v -> v + 1, v -> v + 2);
        }

        System.out.println(l);

        List<Integer> l2 = new List<Integer>(1, 2, 3);

        for (int i = 0; i < 10000; i++) {
            l2 = notFused(l2);
        }

        System.out.println(l2);

        StructuredGraph notFused = getGraph("fff");
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

            System.setProperty("graal.fusing", "org.graalvm.compiler.phases.common.fusing.List:org.graalvm.compiler.phases.common.fusing.ListFusing");

            InliningPhase inliningPhase = new InliningPhase(new CanonicalizerPhase());

            FusingPhase fusingPhase = new FusingPhase(inliningPhase);

            fusingPhase.apply(graph, context);

            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "after fusing ");

            NodeIterable<InvokeNode> nodes2 = graph.getNodes().filter(InvokeNode.class);

            nodes2.forEach(c -> {
                if (c.predecessor().getNodeClass() == c.getNodeClass())
                    System.out.println(c);
            });

// new CanonicalizerPhase().apply(graph, context);
// new DeadCodeEliminationPhase().apply(graph);
// new LoweringPhase(new CanonicalizerPhase(),
// LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
// new LockEliminationPhase().apply(graph);
            return graph;
        }
    }

}
