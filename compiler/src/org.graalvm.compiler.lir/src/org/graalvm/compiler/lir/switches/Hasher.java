package org.graalvm.compiler.lir.switches;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class Hasher {

    public static final Optional<Hasher> forKeys(JavaConstant[] keys) {
        if (keys.length < 2)
            return Optional.empty();
        else {
            assertSorted(keys);
            TreeSet<Hasher> candidates = new TreeSet<>(new Comparator<Hasher>() {
                @Override
                public int compare(Hasher o1, Hasher o2) {
                    int d = o1.cardinality - o2.cardinality;
                    if (d != 0)
                        return d;
                    else
                        return o1.effort() - o2.effort();
                }
            });
            long min = keys[0].asLong();
            for (HashFunction f : HashFunction.instances()) {
                for (int cardinality = keys.length; cardinality < keys.length * 8; cardinality++) {
                    if (isValid(keys, min, f, cardinality)) {
                        candidates.add(new Hasher(f, cardinality, min));
                    }
                }
            }
            if (candidates.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(candidates.first());
            }
        }
    }

    private static void assertSorted(JavaConstant[] keys) {
        for (int i = 1; i < keys.length; i++) {
            assert keys[i - 1].asLong() < keys[i].asLong();
        }
    }

    private static boolean isValid(JavaConstant[] keys, long min, HashFunction function, int cardinality) {
        Set<Integer> seen = new HashSet<>(keys.length);
        for (JavaConstant key : keys) {
            int hash = function.apply(key.asLong(), min) & (cardinality - 1);
            if (seen.contains(hash)) {
                return false;
            } else {
                seen.add(hash);
            }
        }
        return true;
    }

    private final HashFunction function;
    private final int cardinality;
    private final long min;

    private Hasher(HashFunction function, int cardinality, long min) {
        this.function = function;
        this.cardinality = cardinality;
        this.min = min;
    }

    public int hash(long value) {
        return function.apply(value, min) & (cardinality - 1);
    }

    public int effort() {
        return function.effort() + 1;
    }

    public int cardinality() {
        return cardinality;
    }

    public Value gen(Value x, ArithmeticLIRGeneratorTool gen) {
        Value h = function.gen(x, gen.getLIRGen().emitJavaConstant(JavaConstant.forLong(min)), gen);
        return gen.emitAnd(h, gen.getLIRGen().emitJavaConstant(JavaConstant.forInt(cardinality - 1)));
    }

    @Override
    public String toString() {
        return "Hasher[function=" + function + ", effort=" + effort() + ", cardinality=" + cardinality + "]";
    }

}
