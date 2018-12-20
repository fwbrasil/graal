package org.graalvm.compiler.lir.switches;

import java.util.Arrays;
import java.util.Comparator;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.lir.switches.SwitchStrategy.SwitchClosure;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This strategy divides the keys into ranges of successive keys with the same target and
 * creates comparisons for these ranges.
 */
public class RangesStrategy extends PrimitiveStrategy {
    private final Integer[] indexes;

    public RangesStrategy(final double[] keyProbabilities, JavaConstant[] keyConstants) {
        super(keyProbabilities, keyConstants);

        int keyCount = keyConstants.length;
        indexes = new Integer[keyCount];
        for (int i = 0; i < keyCount; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return keyProbabilities[o1] < keyProbabilities[o2] ? 1 : keyProbabilities[o1] > keyProbabilities[o2] ? -1 : 0;
            }
        });
    }

    @Override
    public void run(SwitchClosure closure) {
        int depth = 0;
        closure.conditionalJump(0, Condition.LT, true);
        registerDefaultEffort(++depth);
        int rangeStart = 0;
        int rangeEnd = getSliceEnd(closure, rangeStart);
        while (rangeEnd != keyConstants.length - 1) {
            if (rangeStart == rangeEnd) {
                closure.conditionalJump(rangeStart, Condition.EQ, false);
                registerEffort(rangeStart, rangeEnd, ++depth);
            } else {
                if (rangeStart == 0 || keyConstants[rangeStart - 1].asLong() + 1 != keyConstants[rangeStart].asLong()) {
                    closure.conditionalJump(rangeStart, Condition.LT, true);
                    registerDefaultEffort(++depth);
                }
                closure.conditionalJump(rangeEnd, Condition.LE, false);
                registerEffort(rangeStart, rangeEnd, ++depth);
            }
            rangeStart = rangeEnd + 1;
            rangeEnd = getSliceEnd(closure, rangeStart);
        }
        if (rangeStart == rangeEnd) {
            closure.conditionalJumpOrDefault(rangeStart, Condition.EQ, true);
            registerEffort(rangeStart, rangeEnd, ++depth);
            registerDefaultEffort(depth);
        } else {
            if (rangeStart == 0 || keyConstants[rangeStart - 1].asLong() + 1 != keyConstants[rangeStart].asLong()) {
                closure.conditionalJump(rangeStart, Condition.LT, true);
                registerDefaultEffort(++depth);
            }
            closure.conditionalJumpOrDefault(rangeEnd, Condition.LE, true);
            registerEffort(rangeStart, rangeEnd, ++depth);
            registerDefaultEffort(depth);
        }
    }
}