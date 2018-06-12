package org.graalvm.compiler.phases.common.fusion;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Config {

    public static final Config instance;

    static {
        instance = new Config(entries(parseConfig()));
    }

    private static final Map<Class<?>, Class<?>> parseConfig() {
        Map<Class<?>, Class<?>> configs = new HashMap<>();
        String[] entries = Optional.ofNullable(System.getProperty("fusion")).map(v -> v.split(",")).orElse(new String[0]);
        for (String entry : entries) {
            String[] mapping = entry.split(":");
            if (mapping.length == 2) {
                try {
                    Class<?> targetClass = ClassLoader.getSystemClassLoader().loadClass(mapping[0]);
                    Class<?> fusionClass = ClassLoader.getSystemClassLoader().loadClass(mapping[1]);
                    configs.put(targetClass, fusionClass);
                } catch (Exception e) {
                    // TODO logging
                }
            }
            // else TODO log invalid config
        }
        return configs;
    }

    private static final List<Entry> entries(Map<Class<?>, Class<?>> configs) {
        List<Entry> entries = new ArrayList<>();
        configs.forEach((target, fusion) -> {
            Map<Method, Method> methods = new HashMap<>();
            for (Method m : target.getMethods()) {
                try {
                    if (m.getDeclaringClass() != Object.class) {
                        Method f = fusion.getMethod(m.getName(), m.getParameterTypes());
                        methods.put(m, f);
                    }
                } catch (NoSuchMethodException | SecurityException e) {
                    // TODO logging
                }
            }
            try {
                Method fusionMethod = fusion.getMethod("fuse"); // TODO ensure it's static
                Method stageMethod = fusion.getMethod("stage", target);
                entries.add(new Entry(target, fusion, methods, fusionMethod, stageMethod));
            } catch (NoSuchMethodException | SecurityException e) {
                // TODO logging
            }
        });
        return entries;
    }

    public static class Entry {
        private final Class<?> targetClass;
        private final Class<?> fusionClass;
        private final Map<Method, Method> methods;
        private final Method fuseMethod;
        private final Method stageMethod;

        public Entry(Class<?> targetClass, Class<?> fusionClass, Map<Method, Method> methods, Method fuseMethod, Method stageMethod) {
            super();
            this.targetClass = targetClass;
            this.fusionClass = fusionClass;
            this.methods = methods;
            this.fuseMethod = fuseMethod;
            this.stageMethod = stageMethod;
        }

        public Class<?> getTargetClass() {
            return targetClass;
        }

        public Class<?> getFusionClass() {
            return fusionClass;
        }

        public Map<Method, Method> getMethods() {
            return Collections.unmodifiableMap(methods);
        }

        public Method getFuseMethod() {
            return fuseMethod;
        }

        public Method getStageMethod() {
            return stageMethod;
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
