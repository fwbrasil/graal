package org.graalvm.compiler.core.test.fusion;

import java.util.function.Function;

import org.graalvm.compiler.phases.common.fusion.MethodFusion;

public abstract class ListFusion<T> implements MethodFusion<List<T>> {

    public static <T> ListFusion<T> stage(List<T> l) {
        return new ListFusion.Value<>(l);
    }

    public <U> ListFusion<U> map(Function<T, U> f) {
        return new Map<>(this, f);
    }

    private static class Value<T> extends ListFusion<T> {
        private final List<T> v;

        public Value(List<T> v) {
            this.v = v;
        }

        @Override
        public List<T> fuse() {
            return v;
        }
    }

    private static class Map<T, U> extends ListFusion<U> {
        private final ListFusion<T> stage;
        private final Function<T, U> func;

        public Map(ListFusion<T> stage, Function<T, U> func) {
            this.stage = stage;
            this.func = func;
        }

        @Override
        public List<U> fuse() {
            return stage.fuse().map(func);
        }

        @Override
        public <V> ListFusion<V> map(Function<U, V> f) {
            return new Map<>(stage, func.andThen(f));
        }
    }
}
