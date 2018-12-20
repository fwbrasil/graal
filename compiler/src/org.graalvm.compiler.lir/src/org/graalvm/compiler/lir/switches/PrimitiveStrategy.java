package org.graalvm.compiler.lir.switches;

import org.graalvm.compiler.lir.switches.SwitchStrategy.SwitchClosure;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Base class for strategies that rely on primitive integer keys.
 */
abstract class PrimitiveStrategy extends SwitchStrategy {
    protected final JavaConstant[] keyConstants;

    protected PrimitiveStrategy(double[] keyProbabilities, JavaConstant[] keyConstants) {
        super(keyProbabilities);
        assert keyProbabilities.length == keyConstants.length;
        this.keyConstants = keyConstants;
    }

    @Override
    public JavaConstant[] getKeyConstants() {
        return keyConstants;
    }

    /**
     * Looks for the end of a stretch of key constants that are successive numbers and have the
     * same target.
     */
    protected int getSliceEnd(SwitchClosure closure, int pos) {
        int slice = pos;
        while (slice < (keyConstants.length - 1) && keyConstants[slice + 1].asLong() == keyConstants[slice].asLong() + 1 && closure.isSameTarget(slice, slice + 1)) {
            slice++;
        }
        return slice;
    }
}