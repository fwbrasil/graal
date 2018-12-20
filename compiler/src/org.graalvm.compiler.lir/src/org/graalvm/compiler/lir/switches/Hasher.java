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

    public static void main(String[] args) {
        JavaConstant[] keys = {JavaConstant.forInt(1), JavaConstant.forInt(2), JavaConstant.forInt(3)};
        Optional<Hasher> h = forKeys(keys);
        System.out.println(h);
    }

    private final HashFunction function;
    private final int cardinality;
    private final long min;

    public Hasher(HashFunction function, int cardinality, long min) {
        this.function = function;
        this.cardinality = cardinality;
        this.min = min;
    }

    public int effort() {
        return function.effort() + 1;
    }

    public int cardinality() {
        return cardinality;
    }

    public Value gen(Value x, ArithmeticLIRGeneratorTool gen) {
        Value h = function.gen(x, gen.getLIRGen().emitJavaConstant(JavaConstant.forLong(min)), gen);
        return gen.emitAnd(h, gen.getLIRGen().emitJavaConstant(JavaConstant.forInt(cardinality)));
    }

    @Override
    public String toString() {
        return "Hasher[function=" + function + ", effort=" + effort() + ", cardinality=" + cardinality + "]";
    }

    public static final Optional<Hasher> forKeys(JavaConstant[] keys) {
        if (keys.length < 2)
            return Optional.empty();
        else {
            assertSorted(keys);
            TreeSet<Hasher> candidates = new TreeSet<>(new Comparator<Hasher>() {
                @Override
                public int compare(Hasher o1, Hasher o2) {
                    int d = o1.effort() - o2.effort();
                    if (d != 0)
                        return d;
                    else
                        return o1.cardinality - o2.cardinality;
                }
            });
            long min = keys[0].asLong();
            for (HashFunction f : HashFunction.instances()) {
                for (int cardinality = 0; cardinality < keys.length * 8; cardinality++) {
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
            int hash = function.apply(key.asLong(), min) & cardinality;
            if (seen.contains(hash)) {
                return false;
            } else {
                seen.add(hash);
            }
        }
        return true;
    }

}
