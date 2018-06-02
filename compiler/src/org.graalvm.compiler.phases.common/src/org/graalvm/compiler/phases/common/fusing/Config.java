package org.graalvm.compiler.phases.common.fusing;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Config {

    public static final Config instance;

    static {
        instance = new Config(entries(parseConfig()));
    }

    private static final Map<Class<?>, Class<?>> parseConfig() {
        Map<Class<?>, Class<?>> configs = new HashMap<>();
        String[] entries = System.getProperty("graal.fusing").split(",");
        for (String entry : entries) {
            String[] mapping = entry.split(":");
            if (mapping.length == 2) {
                try {
                    Class<?> targetClass = Class.forName(mapping[0]);
                    Class<?> fusingClass = Class.forName(mapping[1]);
                    configs.put(targetClass, fusingClass);
                } catch (Exception e) {
                    // TODO log
                }
            }
            // else TODO log invalid config
        }
        return configs;
    }

    private static final List<Entry> entries(Map<Class<?>, Class<?>> configs) {
        List<Entry> entries = new ArrayList<>();
        configs.forEach((target, fusing) -> {
            Map<Method, Method> methods = new HashMap<>();
            for (Method m : target.getMethods()) {
                try {
                    Method f = fusing.getMethod(m.getName(), m.getParameterTypes());
                    methods.put(m, f);
                } catch (NoSuchMethodException | SecurityException e) {
                    // TODO logging
                }
            }
            entries.add(new Entry(target, fusing, methods));
        });
        return entries;
    }

    public static class Entry {
        final Class<?> targetClass;
        final Class<?> fusingClass;
        final Map<Method, Method> methods;

        public Entry(Class<?> targetClass, Class<?> fusingClass, Map<Method, Method> methods) {
            super();
            this.targetClass = targetClass;
            this.fusingClass = fusingClass;
            this.methods = methods;
        }

        public Class<?> getTargetClass() {
            return targetClass;
        }

        public Class<?> getFusingClass() {
            return fusingClass;
        }

        public Map<Method, Method> getMethods() {
            return Collections.unmodifiableMap(methods);
        }
    }

    private final List<Entry> entries;

    private Config(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> getEntries() {
        return entries;
    }
}
