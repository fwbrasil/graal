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
        JavaConstant[] keys = {JavaConstant.forInt(314132), JavaConstant.forInt(312132312), JavaConstant.forInt(312132314)};
        System.out.println(forKeys(keys));
    }

    private final HashFunction function;
    private final int cardinality;
    private final long s;

    public Hasher(HashFunction function, int cardinality, long s) {
        this.function = function;
        this.cardinality = cardinality;
        this.s = s;
    }

    public int effort() {
        return function.effort() + 1;
    }

    public int cardinality() {
        return cardinality;
    }

    public Value gen(Value x, ArithmeticLIRGeneratorTool gen) {
        Value h = function.gen(x, gen.getLIRGen().emitJavaConstant(JavaConstant.forLong(s)), gen);
        return gen.emitAnd(h, gen.getLIRGen().emitJavaConstant(JavaConstant.forInt(cardinality)));
    }

    @Override
    public String toString() {
        return "Hasher [function=" + function + ", effort=" + effort() + ", cardinality=" + cardinality + ", s=" + s + "]";
    }

    public static final Optional<Hasher> forKeys(JavaConstant[] keys) {
        if (keys.length < 2)
            return Optional.empty();
        else {
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
            long s = keys[0].asLong();
            for (HashFunction f : HashFunction.instances()) {
                for (int i = 0; i < keys.length * 8; i++) {
                    if (isValid(keys, s, f, i)) {
                        candidates.add(new Hasher(f, i, s));
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

    private static boolean isValid(JavaConstant[] keys, long s, HashFunction f, int i) {
        Set<Integer> seen = new HashSet<>();
        for (JavaConstant key : keys) {
            int hash = f.apply(key.asLong(), s) & i;
            if (seen.contains(hash)) {
                return false;
            } else {
                seen.add(hash);
            }
        }
        return true;
    }

}
