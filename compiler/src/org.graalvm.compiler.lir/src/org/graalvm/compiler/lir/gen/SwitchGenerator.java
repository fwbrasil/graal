package org.graalvm.compiler.lir.gen;

import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.hashing.Hasher;

import jdk.vm.ci.meta.JavaConstant;

abstract class SwitchGenerator {

    protected final LIRGenerator lir;
    protected final JavaConstant[] keyConstants;
    protected final LabelRef[] keyTargets;
    protected final LabelRef defaultTarget;
    protected final Variable value;

    private SwitchGenerator(LIRGenerator lir, JavaConstant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
        this.lir = lir;
        this.keyConstants = keyConstants;
        this.keyTargets = keyTargets;
        this.defaultTarget = defaultTarget;
        this.value = value;
    }

    public abstract double getAverageEffort();

    public abstract int getCodeSizeEstimate();

    public abstract void emit();

    public static final class Strategy extends SwitchGenerator {

        private final SwitchStrategy strategy;

        public Strategy(LIRGenerator lir, SwitchStrategy strategy, JavaConstant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
            super(lir, keyConstants, keyTargets, defaultTarget, value);
            this.strategy = strategy;
        }

        @Override
        public double getAverageEffort() {
            return strategy.getAverageEffort();
        }

        @Override
        public int getCodeSizeEstimate() {
            return strategy.getCodeSizeEstimate();
        }

        @Override
        public void emit() {
            lir.emitStrategySwitch(strategy, value, keyTargets, defaultTarget);
        }

        @Override
        public String toString() {
            return "StrategySwitchAlternative [strategy=" + strategy + ", getAverageEffort()=" + getAverageEffort() + ", getCodeSizeEstimate()=" + getCodeSizeEstimate() + "]";
        }
    }

    public static final class HashTable extends SwitchGenerator {

        public HashTable(LIRGenerator lir, Hasher hasher, JavaConstant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
            super(lir, keyConstants, keyTargets, defaultTarget, value);
            this.hasher = hasher;
        }

        private final Hasher hasher;

        @Override
        public double getAverageEffort() {
            return 4D + hasher.effort() / 10;
        }

        @Override
        public int getCodeSizeEstimate() {
            return (8 * 4) + 8 * keyConstants.length;
        }

        @Override
        public void emit() {
            int keyCount = keyConstants.length;
            int cardinality = hasher.cardinality();
            LabelRef[] targets = new LabelRef[cardinality];
            JavaConstant[] keys = new JavaConstant[cardinality];
            for (int i = 0; i < cardinality; i++) {
                keys[i] = JavaConstant.INT_0;
                targets[i] = defaultTarget;
            }
            for (int i = 0; i < keyCount; i++) {
                int idx = hasher.hash(keyConstants[i].asLong());
                keys[idx] = keyConstants[i];
                targets[idx] = keyTargets[i];
            }
            lir.emitHashTableSwitch(hasher, keys, defaultTarget, targets, value);
        }

        @Override
        public String toString() {
            return "HashTableSwitchAlternative [hasher=" + hasher + ", getAverageEffort()=" + getAverageEffort() + ", getCodeSizeEstimate()=" + getCodeSizeEstimate() + "]";
        }
    }

    public static final class Table extends SwitchGenerator {

        private final long valueRange;

        public Table(LIRGenerator lir, JavaConstant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
            super(lir, keyConstants, keyTargets, defaultTarget, value);
            this.valueRange = keyConstants[keyConstants.length - 1].asLong() - keyConstants[0].asLong() + 1;
        }

        @Override
        public double getAverageEffort() {
            return 4D;
        }

        @Override
        public int getCodeSizeEstimate() {
            return (int) ((8 * 4) + 4 * valueRange);
        }

        @Override
        public void emit() {
            int keyCount = keyConstants.length;
            int minValue = keyConstants[0].asInt();
            assert valueRange < Integer.MAX_VALUE;
            LabelRef[] targets = new LabelRef[(int) valueRange];
            for (int i = 0; i < valueRange; i++) {
                targets[i] = defaultTarget;
            }
            for (int i = 0; i < keyCount; i++) {
                targets[keyConstants[i].asInt() - minValue] = keyTargets[i];
            }
            lir.emitTableSwitch(minValue, defaultTarget, targets, value);
        }

        @Override
        public String toString() {
            return "TableSwitchAlternative [getAverageEffort()=" + getAverageEffort() + ", getCodeSizeEstimate()=" + getCodeSizeEstimate() + "]";
        }
    }
}