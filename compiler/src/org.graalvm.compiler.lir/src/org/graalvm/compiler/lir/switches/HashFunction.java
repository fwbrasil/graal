package org.graalvm.compiler.lir.switches;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

abstract class HashFunction {

    abstract public int apply(long x, long s);

    abstract public int effort();

    abstract public Value gen(Value x, Value s, ArithmeticLIRGeneratorTool gen);

    public static Set<HashFunction> instances() {
        return Collections.unmodifiableSet(instances);
    }

    private static final Set<HashFunction> instances = new TreeSet<>(new Comparator<HashFunction>() {
        @Override
        public int compare(HashFunction o1, HashFunction o2) {
            return o1.effort() - o2.effort();
        }
    });

    private static final int[] mersennePrimes = {3, 7, 31, 127, 8191, 131071, 524287, 2147483647};

    static {
      //@formatter:off
        add("x", 0,
            (x, s) -> x,
            gen -> (x, s) -> x);

        add("x - s", 1,
            (x, s) -> x - s,
            gen -> (x, s) -> gen.emitSub(x, s, false));

        add("x ^ s", 1,
            (x, s) -> x ^ s,
            gen -> (x, s) -> gen.emitXor(x, s));

        add("x & s", 1,
            (x, s) -> x & s,
            gen -> (x, s) -> gen.emitAnd(x, s));

        add("x >> s", 1,
            (x, s) -> x >> s,
            gen -> (x, s) -> gen.emitShr(x, s));

        add("x >> (x & s)", 2,
            (x, s) -> x >> (x & s),
            gen -> (x, s) -> gen.emitShr(x, gen.emitAnd(x, s)));

        add("(x >> s) ^ x", 2,
            (x, s) -> (x >> s) ^ x,
            gen -> (x, s) -> gen.emitXor(gen.emitShr(x, s), x));

        add("(x >> s) * x", 3,
            (x, s) -> (x >> s) * x,
            gen -> (x, s) -> gen.emitMul(gen.emitShr(x, s), x, false));

        addWithPrimes("(x * p) >> s", 3,
                      p -> (x, s) -> (x * p) >> s,
                      (gen, p) -> (x, s) -> gen.emitShr(gen.emitMul(x, p, false), s));

        addWithPrimes("rotateRight(x, p)", 5,
                      p -> (x, s) -> Long.rotateRight(x, p),
                      (gen, p) -> (x, s) -> rotateRight(gen, x, p));

        addWithPrimes("rotateRight(x, p) + x", 6,
                      p -> (x, s) -> Long.rotateRight(x, p) + x,
                      (gen, p) -> (x, s) -> gen.emitAdd(rotateRight(gen, x, p), x, false));

        addWithPrimes("rotateRight(x, p) ^ x", 2,
                      p -> (x, s) -> Long.rotateRight(x, p) ^ x,
                      (gen, p) -> (x, s) -> gen.emitXor(rotateRight(gen, x, p), x));
      //@formatter:on
    }

    private static final Value rotateRight(ArithmeticLIRGeneratorTool gen, Value i, Value distance) {
        // (i >>> distance) | (i << -distance)
        return gen.emitOr(gen.emitUShr(i, distance), gen.emitShl(i, gen.emitNegate(distance)));
    }

    private static final void add(String toString, int effort, BiFunction<Long, Long, Long> f, Function<ArithmeticLIRGeneratorTool, BiFunction<Value, Value, Value>> gen) {
        instances.add(new HashFunction() {

            @Override
            public int apply(long x, long s) {
                return f.apply(x, s).intValue();
            }

            @Override
            public int effort() {
                return effort;
            }

            @Override
            public String toString() {
                return toString;
            }

            @Override
            public Value gen(Value x, Value s, ArithmeticLIRGeneratorTool t) {
                return gen.apply(t).apply(x, s);
            }
        });
    }

    private static final void addWithPrimes(String toString, int effort, Function<Integer, BiFunction<Long, Long, Long>> f,
                    BiFunction<ArithmeticLIRGeneratorTool, Value, BiFunction<Value, Value, Value>> gen) {
        for (int p : mersennePrimes) {
            add(toString, effort, f.apply(p), g -> gen.apply(g, g.getLIRGen().emitJavaConstant(JavaConstant.forInt(p))));
        }
    }
}
