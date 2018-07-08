package org.graalvm.compiler.core.test.fusion;

import java.util.Arrays;
import java.util.function.Function;

public interface List<T> {

    public static <T> List<T> apply(T... values) {
        return new ArrayList<>(values);
    }

    <U> List<U> map(Function<T, U> f);

    <U> List<U> fusedMap(Function<T, U> f);
}

class ArrayList<T> implements List<T> {
    T[] values;

    public ArrayList(T[] values) {
        this.values = values;
    }

    @Override
    public <U> List<U> map(Function<T, U> f) {
        U[] n = (U[]) new Object[values.length];
        for (int i = 0; i < values.length; i++)
            n[i] = f.apply(values[i]);
        return new ArrayList<>(n);
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
        ArrayList other = (ArrayList) obj;
        if (!Arrays.equals(values, other.values))
            return false;
        return true;
    }

    @Override
    public <U> List<U> fusedMap(Function<T, U> f) {
        return new FusedMap<>(this, f);
    }
}

class FusedMap<T, U> implements List<U> {

    ArrayList<T> base;
    Function<T, U> map;

    public FusedMap(ArrayList<T> base, Function<T, U> map) {
        super();
        this.base = base;
        this.map = map;
    }

    @Override
    public <V> List<V> map(Function<U, V> f) {
        return base.map(map.andThen(f));
    }

    @Override
    public <V> List<V> fusedMap(Function<U, V> f) {
        return new FusedMap<>(base, map.andThen(f));
    }
}
