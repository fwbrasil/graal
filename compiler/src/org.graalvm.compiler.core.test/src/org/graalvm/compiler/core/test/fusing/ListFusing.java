package org.graalvm.compiler.core.test.fusing;

import java.util.function.Function;
import org.graalvm.compiler.phases.common.fusing.Fusing;

public abstract class ListFusing<T> implements Fusing<List<T>> {

    public static <T> ListFusing<T> apply(List<T> l) {
        return new ListFusing.Value<>(l);
    }

    public <U> ListFusing<U> map(Function<T, U> f) {
        return new Map<>(this, f);
    }

    private static class Value<T> extends ListFusing<T> {
        private final List<T> v;

        public Value(List<T> v) {
            this.v = v;
        }

        @Override
        public List<T> fuse() {
            return v;
        }
    }

    private static class Map<T, U> extends ListFusing<U> {
        private final ListFusing<T> stage;
        private final Function<T, U> func;

        public Map(ListFusing<T> stage, Function<T, U> func) {
            this.stage = stage;
            this.func = func;
        }

        @Override
        public List<U> fuse() {
            return stage.fuse().map(func);
        }

        @Override
        public <V> ListFusing<V> map(Function<U, V> f) {
            return new Map<>(stage, func.andThen(f));
        }
    }
}
