package org.graalvm.compiler.core.test.fusing;

import java.util.Arrays;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(values);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        List other = (List) obj;
        if (!Arrays.equals(values, other.values))
            return false;
        return true;
    }

}
