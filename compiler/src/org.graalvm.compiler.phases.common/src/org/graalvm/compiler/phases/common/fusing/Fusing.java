package org.graalvm.compiler.phases.common.fusing;

public interface Fusing<T, S extends Fusing.Stage<T>> {

    public static interface Stage<U> {
        U apply();
    }

    public S stage(T v);
}
