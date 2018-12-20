/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.lir.switches;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * This class encapsulates different strategies on how to generate code for switch instructions.
 *
 * The {@link #getBestStrategy(double[], JavaConstant[], LabelRef[])} method can be used to get
 * strategy with the smallest average effort (average number of comparisons until a decision is
 * reached). The strategy returned by this method will have its averageEffort set, while a strategy
 * constructed directly will not.
 */
public abstract class SwitchStrategy {

    interface SwitchClosure {
        /**
         * Generates a conditional or unconditional jump. The jump will be unconditional if
         * condition is null. If defaultTarget is true, then the jump will go the default.
         *
         * @param index Index of the value and the jump target (only used if defaultTarget == false)
         * @param condition The condition on which to jump (can be null)
         * @param defaultTarget true if the jump should go to the default target, false if index
         *            should be used.
         */
        void conditionalJump(int index, Condition condition, boolean defaultTarget);

        /**
         * Generates a conditional jump to the target with the specified index. The fall through
         * should go to the default target.
         *
         * @param index Index of the value and the jump target
         * @param condition The condition on which to jump
         * @param canFallThrough true if this is the last instruction in the switch statement, to
         *            allow for fall-through optimizations.
         */
        void conditionalJumpOrDefault(int index, Condition condition, boolean canFallThrough);

        /**
         * Create a new label and generate a conditional jump to it.
         *
         * @param index Index of the value and the jump target
         * @param condition The condition on which to jump
         * @return a new Label
         */
        Label conditionalJump(int index, Condition condition);

        /**
         * Binds a label returned by {@link #conditionalJump(int, Condition)}.
         */
        void bind(Label label);

        /**
         * Return true iff the target of both indexes is the same.
         */
        boolean isSameTarget(int index1, int index2);
    }

    /**
     * Backends can subclass this abstract class and generate code for switch strategies by
     * implementing the {@link #conditionalJump(int, Condition, Label)} method.
     */
    public abstract static class BaseSwitchClosure implements SwitchClosure {

        private final CompilationResultBuilder crb;
        private final Assembler masm;
        private final LabelRef[] keyTargets;
        private final LabelRef defaultTarget;

        public BaseSwitchClosure(CompilationResultBuilder crb, Assembler masm, LabelRef[] keyTargets, LabelRef defaultTarget) {
            this.crb = crb;
            this.masm = masm;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
        }

        /**
         * This method generates code for a comparison between the actual value and the constant at
         * the given index and a condition jump to target.
         */
        protected abstract void conditionalJump(int index, Condition condition, Label target);

        @Override
        public void conditionalJump(int index, Condition condition, boolean targetDefault) {
            Label target = targetDefault ? defaultTarget.label() : keyTargets[index].label();
            if (condition == null) {
                masm.jmp(target);
            } else {
                conditionalJump(index, condition, target);
            }
        }

        @Override
        public void conditionalJumpOrDefault(int index, Condition condition, boolean canFallThrough) {
            if (canFallThrough && crb.isSuccessorEdge(defaultTarget)) {
                conditionalJump(index, condition, keyTargets[index].label());
            } else if (canFallThrough && crb.isSuccessorEdge(keyTargets[index])) {
                conditionalJump(index, condition.negate(), defaultTarget.label());
            } else {
                conditionalJump(index, condition, keyTargets[index].label());
                masm.jmp(defaultTarget.label());
            }
        }

        @Override
        public Label conditionalJump(int index, Condition condition) {
            Label label = new Label();
            conditionalJump(index, condition, label);
            return label;
        }

        @Override
        public void bind(Label label) {
            masm.bind(label);
        }

        @Override
        public boolean isSameTarget(int index1, int index2) {
            return keyTargets[index1] == keyTargets[index2];
        }

    }

    /**
     * This closure is used internally to determine the average effort for a certain strategy on a
     * given switch instruction.
     */
    private class EffortClosure implements SwitchClosure {

        private int defaultEffort;
        private int defaultCount;
        private final int[] keyEfforts = new int[keyProbabilities.length];
        private final int[] keyCounts = new int[keyProbabilities.length];
        private final LabelRef[] keyTargets;

        EffortClosure(LabelRef[] keyTargets) {
            this.keyTargets = keyTargets;
        }

        @Override
        public void conditionalJump(int index, Condition condition, boolean defaultTarget) {
            // nothing to do
        }

        @Override
        public void conditionalJumpOrDefault(int index, Condition condition, boolean canFallThrough) {
            // nothing to do
        }

        @Override
        public Label conditionalJump(int index, Condition condition) {
            // nothing to do
            return null;
        }

        @Override
        public void bind(Label label) {
            // nothing to do
        }

        @Override
        public boolean isSameTarget(int index1, int index2) {
            return keyTargets[index1] == keyTargets[index2];
        }

        public double getAverageEffort() {
            double defaultProbability = 1;
            double effort = 0;
            for (int i = 0; i < keyProbabilities.length; i++) {
                effort += keyEfforts[i] * keyProbabilities[i] / keyCounts[i];
                defaultProbability -= keyProbabilities[i];
            }
            return effort + defaultEffort * defaultProbability / defaultCount;
        }
    }

    public final double[] keyProbabilities;
    private double averageEffort = -1;
    private EffortClosure effortClosure;

    public SwitchStrategy(double[] keyProbabilities) {
        assert keyProbabilities.length >= 2;
        this.keyProbabilities = keyProbabilities;
    }

    public abstract Constant[] getKeyConstants();

    public double getAverageEffort() {
        assert averageEffort >= 0 : "average effort was not calculated yet for this strategy";
        return averageEffort;
    }

    /**
     * Tells the system that the given (inclusive) range of keys is reached after depth number of
     * comparisons, which is used to calculate the average effort.
     */
    protected void registerEffort(int rangeStart, int rangeEnd, int depth) {
        if (effortClosure != null) {
            for (int i = rangeStart; i <= rangeEnd; i++) {
                effortClosure.keyEfforts[i] += depth;
                effortClosure.keyCounts[i]++;
            }
        }
    }

    /**
     * Tells the system that the default successor is reached after depth number of comparisons,
     * which is used to calculate average effort.
     */
    protected void registerDefaultEffort(int depth) {
        if (effortClosure != null) {
            effortClosure.defaultEffort += depth;
            effortClosure.defaultCount++;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[avgEffort=" + averageEffort + "]";
    }

    public abstract void run(SwitchClosure closure);

    private static SwitchStrategy[] getStrategies(double[] keyProbabilities, JavaConstant[] keyConstants, LabelRef[] keyTargets) {
        SwitchStrategy[] strategies = new SwitchStrategy[]{new SequentialStrategy(keyProbabilities, keyConstants), new RangesStrategy(keyProbabilities, keyConstants),
                        new BinaryStrategy(keyProbabilities, keyConstants)};
        for (SwitchStrategy strategy : strategies) {
            strategy.effortClosure = strategy.new EffortClosure(keyTargets);
            strategy.run(strategy.effortClosure);
            strategy.averageEffort = strategy.effortClosure.getAverageEffort();
            strategy.effortClosure = null;
        }
        return strategies;
    }

    /**
     * Creates all switch strategies for the given switch, evaluates them (based on average effort)
     * and returns the best one.
     */
    public static SwitchStrategy getBestStrategy(double[] keyProbabilities, JavaConstant[] keyConstants, LabelRef[] keyTargets) {
        SwitchStrategy[] strategies = getStrategies(keyProbabilities, keyConstants, keyTargets);
        double bestEffort = Integer.MAX_VALUE;
        SwitchStrategy bestStrategy = null;
        for (SwitchStrategy strategy : strategies) {
            if (strategy.getAverageEffort() < bestEffort) {
                bestEffort = strategy.getAverageEffort();
                bestStrategy = strategy;
            }
        }
        return bestStrategy;
    }
}
