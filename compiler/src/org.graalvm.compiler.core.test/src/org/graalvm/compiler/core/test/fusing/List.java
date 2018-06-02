package org.graalvm.compiler.core.test.fusing;

import java.util.function.Function;

public class List<T> {
    T[] values;

    public List(T... values) {
        this.values = values;
    }

    public int size() {
        return values.length;
    }

    public <U> List<U> map(Function<T, U> f) {
        U[] n = (U[]) new Object[values.length];
        for (int i = 0; i < values.length; i++)
            n[i] = f.apply(values[i]);
        return new List(n);
    }
}
