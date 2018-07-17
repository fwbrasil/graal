package org.graalvm.compiler.phases.common.fusion;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.sun.xml.internal.ws.util.StringUtils;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodFusionConfig {

    public static final Map<Method, Method> fusionMap;

    private static final ThreadLocal<Boolean> inlineAll = new ThreadLocal<Boolean>() {
        @Override
        public Boolean initialValue() {
            return false;
        }
    };

    public static void withInlineAll(Runnable r) {
        Boolean prev = inlineAll.get();
        inlineAll.set(true);
        try {
            r.run();
        } finally {
            inlineAll.set(prev);
        }
    }

    public static boolean shouldInline(ResolvedJavaMethod m) {
        if (inlineAll.get())
            return true;
        for (Method toFuse : fusionMap.keySet())
            // TODO weak check
            if (toFuse.getName().equals(m.getName()) && m.getDeclaringClass().getName().replace('/', '.').contains(toFuse.getDeclaringClass().getName()))
                return false;
        return true;
    }

    static {
        fusionMap = new HashMap<>();
        String fuseClass = System.getProperty("FuseClass");
        if (fuseClass != null) {
            Class<?> cls;
            try {
                cls = ClassLoader.getSystemClassLoader().loadClass(fuseClass);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            Map<String, Method> byName = new HashMap<>();
            for (Method m : cls.getMethods())
                byName.put(m.getName(), m);

            for (Method m : cls.getMethods()) {
                String fusedName = "fused" + StringUtils.capitalize(m.getName());
                if (byName.containsKey(fusedName)) {
                    Method fusedMethod = byName.get(fusedName);
                    if (fusedMethod.getReturnType().equals(m.getReturnType()) &&
                                    Arrays.equals(m.getParameterTypes(), fusedMethod.getParameterTypes()))
                        fusionMap.put(m, fusedMethod);
                }
            }
        }
    }

}