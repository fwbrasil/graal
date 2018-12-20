package org.graalvm.compiler.lir.switches;

import java.util.Arrays;
import java.util.Comparator;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.lir.switches.SwitchStrategy.SwitchClosure;

import jdk.vm.ci.meta.Constant;

/**
 * This strategy orders the keys according to their probability and creates one equality
 * comparison per key.
 */
public class SequentialStrategy extends SwitchStrategy {
    private final Integer[] indexes;
    private final Constant[] keyConstants;

    public SequentialStrategy(final double[] keyProbabilities, Constant[] keyConstants) {
        super(keyProbabilities);
        assert keyProbabilities.length == keyConstants.length;

        this.keyConstants = keyConstants;
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
    public Constant[] getKeyConstants() {
        return keyConstants;
    }

    @Override
    public void run(SwitchClosure closure) {
        for (int i = 0; i < keyConstants.length - 1; i++) {
            closure.conditionalJump(indexes[i], Condition.EQ, false);
            registerEffort(indexes[i], indexes[i], i + 1);
        }
        closure.conditionalJumpOrDefault(indexes[keyConstants.length - 1], Condition.EQ, true);
        registerEffort(indexes[keyConstants.length - 1], indexes[keyConstants.length - 1], keyConstants.length);
        registerDefaultEffort(keyConstants.length);
    }
}