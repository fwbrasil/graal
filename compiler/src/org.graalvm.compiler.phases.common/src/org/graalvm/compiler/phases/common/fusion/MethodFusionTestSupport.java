package org.graalvm.compiler.phases.common.fusion;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class MethodFusionTestSupport {

    /**
     * Executes the `call` with and without method fusion enabled and calls the consumer to verify
     * that the results are the same.
     *
     * @param call supplier that makes the call to the test method subject to fusion
     * @param verify consumer that verifies the result (left is without fusion, right with)
     * @return boolean indicating if method fusion was applied
     */
    public static <T> boolean compareFused(Supplier<T> call, BiConsumer<T, T> verify) {
        return true; // TODO
    }

    /**
     * Checks a target class to validate if the fusion will be applied at runtime.
     *
     * @param targetClass the target class with the @MethodFusion annotation.
     */
    public static void verify(Class<?> targetClass) {
        // TODO
    }
}
