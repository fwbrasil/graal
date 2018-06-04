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
                    Class<?> targetClass = ClassLoader.getSystemClassLoader().loadClass(mapping[0]);
                    Class<?> fusingClass = ClassLoader.getSystemClassLoader().loadClass(mapping[1]);
                    configs.put(targetClass, fusingClass);
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
        configs.forEach((target, fusing) -> {
            Map<Method, Method> methods = new HashMap<>();
            for (Method m : target.getMethods()) {
                try {
                    if (m.getDeclaringClass() != Object.class) {
                        Method f = fusing.getMethod(m.getName(), m.getParameterTypes());
                        methods.put(m, f);
                    }
                } catch (NoSuchMethodException | SecurityException e) {
                    e.printStackTrace(); // TODO logging
                }
            }
            try {
                Method fusingMethod = fusing.getMethod("fuse"); // TODO ensure it's static
                Method stageMethod = fusing.getMethod("stage", target);
                entries.add(new Entry(target, fusing, methods, fusingMethod, stageMethod));
            } catch (NoSuchMethodException | SecurityException e) {
                e.printStackTrace(); // TODO logging
            }
        });
        return entries;
    }

    public static class Entry {
        private final Class<?> targetClass;
        private final Class<?> fusingClass;
        private final Map<Method, Method> methods;
        private final Method fuseMethod;
        private final Method stageMethod;

        public Entry(Class<?> targetClass, Class<?> fusingClass, Map<Method, Method> methods, Method fuseMethod, Method stageMethod) {
            super();
            this.targetClass = targetClass;
            this.fusingClass = fusingClass;
            this.methods = methods;
            this.fuseMethod = fuseMethod;
            this.stageMethod = stageMethod;
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
