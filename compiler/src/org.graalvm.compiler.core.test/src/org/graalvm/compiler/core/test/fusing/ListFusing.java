package org.graalvm.compiler.core.test.fusing;

import java.util.function.Function;

public class ListFusing<T> implements Fusing<List<T>, ListFusing.ListStage<T>> {
    @Override
    public ListStage<T> stage(List<T> l) {
        return new ListStage.Value(l);
    }

    public static abstract class ListStage<T> implements Fusing.Stage<List<T>> {

        public <U> ListStage<U> map(Function<T, U> f) {
            return new Map(this, f);
        }

        private static class Value<T> extends ListStage<T> {
            private final List<T> v;

            public Value(List<T> v) {
                this.v = v;
            }

            @Override
            public List<T> apply() {
                return v;
            }
        }

        private static class Map<T, U> extends ListStage<U> {
            private final ListStage<T> stage;
            private final Function<T, U> func;

            public Map(ListStage<T> stage, Function<T, U> func) {
                this.stage = stage;
                this.func = func;
            }

            @Override
            public List<U> apply() {
                return stage.apply().map(func);
            }

            @Override
            public <V> ListStage<V> map(Function<U, V> f) {
                return new Map(stage, func.andThen(f));
            }
        }
    }
}
