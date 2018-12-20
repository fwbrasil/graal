package org.graalvm.compiler.lir.switches;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.lir.switches.SwitchStrategy.SwitchClosure;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This strategy recursively subdivides the list of keys to create a binary search based on
 * probabilities.
 */
public class BinaryStrategy extends PrimitiveStrategy {

    private static final double MIN_PROBABILITY = 0.00001;

    private final double[] probabilitySums;

    public BinaryStrategy(double[] keyProbabilities, JavaConstant[] keyConstants) {
        super(keyProbabilities, keyConstants);
        probabilitySums = new double[keyProbabilities.length + 1];
        double sum = 0;
        for (int i = 0; i < keyConstants.length; i++) {
            sum += Math.max(keyProbabilities[i], MIN_PROBABILITY);
            probabilitySums[i + 1] = sum;
        }
    }

    @Override
    public void run(SwitchClosure closure) {
        recurseBinarySwitch(closure, 0, keyConstants.length - 1, 0);
    }

    /**
     * Recursively generate a list of comparisons that always subdivides the keys in the given
     * (inclusive) range in the middle (in terms of probability, not index). If left is bigger
     * than zero, then we always know that the value is equal to or bigger than the left key.
     * This does not hold for the right key, as there may be a gap afterwards.
     */
    private void recurseBinarySwitch(SwitchClosure closure, int left, int right, int startDepth) {
        assert startDepth < keyConstants.length * 3 : "runaway recursion in binary switch";
        int depth = startDepth;
        boolean leftBorder = left == 0;
        boolean rightBorder = right == keyConstants.length - 1;

        if (left + 1 == right) {
            // only two possible values
            if (leftBorder || rightBorder || keyConstants[right].asLong() + 1 != keyConstants[right + 1].asLong() || keyConstants[left].asLong() + 1 != keyConstants[right].asLong()) {
                closure.conditionalJump(left, Condition.EQ, false);
                registerEffort(left, left, ++depth);
                closure.conditionalJumpOrDefault(right, Condition.EQ, rightBorder);
                registerEffort(right, right, ++depth);
                registerDefaultEffort(depth);
            } else {
                // here we know that the value can only be one of these two keys in the range
                closure.conditionalJump(left, Condition.EQ, false);
                registerEffort(left, left, ++depth);
                closure.conditionalJump(right, null, false);
                registerEffort(right, right, depth);
            }
            return;
        }
        double probabilityStart = probabilitySums[left];
        double probabilityMiddle = (probabilityStart + probabilitySums[right + 1]) / 2;
        assert probabilityMiddle >= probabilityStart;
        int middle = left;
        while (getSliceEnd(closure, middle + 1) < right && probabilitySums[getSliceEnd(closure, middle + 1)] < probabilityMiddle) {
            middle = getSliceEnd(closure, middle + 1);
        }
        middle = getSliceEnd(closure, middle);
        assert middle < keyConstants.length - 1;

        if (getSliceEnd(closure, left) == middle) {
            if (left == 0) {
                closure.conditionalJump(0, Condition.LT, true);
                registerDefaultEffort(++depth);
            }
            closure.conditionalJump(middle, Condition.LE, false);
            registerEffort(left, middle, ++depth);

            if (middle + 1 == right) {
                closure.conditionalJumpOrDefault(right, Condition.EQ, rightBorder);
                registerEffort(right, right, ++depth);
                registerDefaultEffort(depth);
            } else {
                if (keyConstants[middle].asLong() + 1 != keyConstants[middle + 1].asLong()) {
                    closure.conditionalJump(middle + 1, Condition.LT, true);
                    registerDefaultEffort(++depth);
                }
                if (getSliceEnd(closure, middle + 1) == right) {
                    if (right == keyConstants.length - 1 || keyConstants[right].asLong() + 1 != keyConstants[right + 1].asLong()) {
                        closure.conditionalJumpOrDefault(right, Condition.LE, rightBorder);
                        registerEffort(middle + 1, right, ++depth);
                        registerDefaultEffort(depth);
                    } else {
                        closure.conditionalJump(middle + 1, null, false);
                        registerEffort(middle + 1, right, depth);
                    }
                } else {
                    recurseBinarySwitch(closure, middle + 1, right, depth);
                }
            }
        } else if (getSliceEnd(closure, middle + 1) == right) {
            if (rightBorder || keyConstants[right].asLong() + 1 != keyConstants[right + 1].asLong()) {
                closure.conditionalJump(right, Condition.GT, true);
                registerDefaultEffort(++depth);
            }
            closure.conditionalJump(middle + 1, Condition.GE, false);
            registerEffort(middle + 1, right, ++depth);
            recurseBinarySwitch(closure, left, middle, depth);
        } else {
            Label label = closure.conditionalJump(middle + 1, Condition.GE);
            depth++;
            recurseBinarySwitch(closure, left, middle, depth);
            closure.bind(label);
            recurseBinarySwitch(closure, middle + 1, right, depth);
        }
    }
}