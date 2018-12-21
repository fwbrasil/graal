/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.jtt.optimize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.lir.hashing.HashFunction;
import org.graalvm.compiler.lir.hashing.Hasher;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;

/*
 * Tests optimization of hash table switches.
 * Code generated by `Switch03.TestGenerator.main`
 */
public class Switch03 extends JTTTest {
    @Test
    public void checkHashFunctionInstances() {
        List<String> coveredByTestCases = Arrays.asList("val >> min", "val", "val >> (val & min)", "(val >> min) ^ val", "val - min", "rotateRight(val, prime)", "rotateRight(val, prime) ^ val",
                        "rotateRight(val, prime) + val", "(val >> min) * val", "(val * prime) >> min");
        Set<String> functions = HashFunction.instances().stream().map(Object::toString).collect(Collectors.toSet());
        functions.removeAll(coveredByTestCases);
        assertTrue("The following hash functions are not covered by the `Switch03` test: " + functions +
                        ". Re-run the `Switch03.TestGenerator.main` and update the test class.", functions.isEmpty());
    }

    // Hasher[function=rotateRight(val, prime), effort=4, cardinality=16]
    public static int test1(int arg) {
        switch (arg) {
            case 3080012:
                return 3080012;
            case 3080017:
                return 3080017;
            case 3080029:
                return 3080029;
            case 3080037:
                return 3080037;
            case 3080040:
                return 3080040;
            case 3080054:
                return 3080054;
            case 3080060:
                return 3080060;
            case 3080065:
                return 3080065;
            case 3080073:
                return 3080073;
            case 3080082:
                return 3080082;
            case 3080095:
                return 3080095;
            case 3080103:
                return 3080103;
            case 3080116:
                return 3080116;
            case 3080127:
                return 3080127;
            case 3080130:
                return 3080130;
            default:
                return -1;
        }
    }

    @Test
    public void run1() throws Throwable {
        runTest("test1", 0); // zero
        runTest("test1", 3080011); // bellow
        runTest("test1", 3080012); // first
        runTest("test1", 3080065); // middle
        runTest("test1", 3080130); // last
        runTest("test1", 3080131); // above
        runTest("test1", 3080013); // miss
    }

    // Hasher[function=rotateRight(val, prime) ^ val, effort=5, cardinality=28]
    public static int test2(int arg) {
        switch (arg) {
            case 718707335:
                return 718707335;
            case 718707336:
                return 718707336;
            case 718707347:
                return 718707347;
            case 718707359:
                return 718707359;
            case 718707366:
                return 718707366;
            case 718707375:
                return 718707375;
            case 718707378:
                return 718707378;
            case 718707386:
                return 718707386;
            case 718707396:
                return 718707396;
            case 718707401:
                return 718707401;
            case 718707408:
                return 718707408;
            case 718707409:
                return 718707409;
            case 718707420:
                return 718707420;
            case 718707431:
                return 718707431;
            case 718707436:
                return 718707436;
            default:
                return -1;
        }
    }

    @Test
    public void run2() throws Throwable {
        runTest("test2", 0); // zero
        runTest("test2", 718707334); // bellow
        runTest("test2", 718707335); // first
        runTest("test2", 718707386); // middle
        runTest("test2", 718707436); // last
        runTest("test2", 718707437); // above
        runTest("test2", 718707337); // miss
    }

    // Hasher[function=(val * prime) >> min, effort=4, cardinality=16]
    public static int test3(int arg) {
        switch (arg) {
            case 880488712:
                return 880488712;
            case 880488723:
                return 880488723;
            case 880488737:
                return 880488737;
            case 880488744:
                return 880488744;
            case 880488752:
                return 880488752;
            case 880488757:
                return 880488757;
            case 880488767:
                return 880488767;
            case 880488777:
                return 880488777;
            case 880488781:
                return 880488781;
            case 880488794:
                return 880488794;
            case 880488795:
                return 880488795;
            case 880488807:
                return 880488807;
            case 880488814:
                return 880488814;
            case 880488821:
                return 880488821;
            case 880488831:
                return 880488831;
            default:
                return -1;
        }
    }

    @Test
    public void run3() throws Throwable {
        runTest("test3", 0); // zero
        runTest("test3", 880488711); // bellow
        runTest("test3", 880488712); // first
        runTest("test3", 880488777); // middle
        runTest("test3", 880488831); // last
        runTest("test3", 880488832); // above
        runTest("test3", 880488713); // miss
    }

    // Hasher[function=rotateRight(val, prime) + val, effort=5, cardinality=28]
    public static int test4(int arg) {
        switch (arg) {
            case 189404658:
                return 189404658;
            case 189404671:
                return 189404671;
            case 189404678:
                return 189404678;
            case 189404680:
                return 189404680;
            case 189404687:
                return 189404687;
            case 189404698:
                return 189404698;
            case 189404699:
                return 189404699;
            case 189404711:
                return 189404711;
            case 189404724:
                return 189404724;
            case 189404725:
                return 189404725;
            case 189404732:
                return 189404732;
            case 189404739:
                return 189404739;
            case 189404748:
                return 189404748;
            case 189404754:
                return 189404754;
            case 189404765:
                return 189404765;
            default:
                return -1;
        }
    }

    @Test
    public void run4() throws Throwable {
        runTest("test4", 0); // zero
        runTest("test4", 189404657); // bellow
        runTest("test4", 189404658); // first
        runTest("test4", 189404711); // middle
        runTest("test4", 189404765); // last
        runTest("test4", 189404766); // above
        runTest("test4", 189404659); // miss
    }

    // Hasher[function=val - min, effort=2, cardinality=24]
    public static int test5(int arg) {
        switch (arg) {
            case 527674226:
                return 527674226;
            case 527674235:
                return 527674235;
            case 527674236:
                return 527674236;
            case 527674247:
                return 527674247;
            case 527674251:
                return 527674251;
            case 527674253:
                return 527674253;
            case 527674257:
                return 527674257;
            case 527674263:
                return 527674263;
            case 527674265:
                return 527674265;
            case 527674272:
                return 527674272;
            case 527674286:
                return 527674286;
            case 527674293:
                return 527674293;
            case 527674294:
                return 527674294;
            case 527674306:
                return 527674306;
            case 527674308:
                return 527674308;
            default:
                return -1;
        }
    }

    @Test
    public void run5() throws Throwable {
        runTest("test5", 0); // zero
        runTest("test5", 527674225); // bellow
        runTest("test5", 527674226); // first
        runTest("test5", 527674263); // middle
        runTest("test5", 527674308); // last
        runTest("test5", 527674309); // above
        runTest("test5", 527674227); // miss
    }

    // Hasher[function=val, effort=1, cardinality=24]
    public static int test6(int arg) {
        switch (arg) {
            case 676979121:
                return 676979121;
            case 676979128:
                return 676979128;
            case 676979135:
                return 676979135;
            case 676979146:
                return 676979146;
            case 676979148:
                return 676979148;
            case 676979156:
                return 676979156;
            case 676979158:
                return 676979158;
            case 676979169:
                return 676979169;
            case 676979175:
                return 676979175;
            case 676979179:
                return 676979179;
            case 676979182:
                return 676979182;
            case 676979194:
                return 676979194;
            case 676979200:
                return 676979200;
            case 676979205:
                return 676979205;
            case 676979219:
                return 676979219;
            default:
                return -1;
        }
    }

    @Test
    public void run6() throws Throwable {
        runTest("test6", 0); // zero
        runTest("test6", 676979120); // bellow
        runTest("test6", 676979121); // first
        runTest("test6", 676979169); // middle
        runTest("test6", 676979219); // last
        runTest("test6", 676979220); // above
        runTest("test6", 676979122); // miss
    }

    // Hasher[function=(val >> min) ^ val, effort=3, cardinality=16]
    public static int test7(int arg) {
        switch (arg) {
            case 634218696:
                return 634218696;
            case 634218710:
                return 634218710;
            case 634218715:
                return 634218715;
            case 634218720:
                return 634218720;
            case 634218724:
                return 634218724;
            case 634218732:
                return 634218732;
            case 634218737:
                return 634218737;
            case 634218749:
                return 634218749;
            case 634218751:
                return 634218751;
            case 634218758:
                return 634218758;
            case 634218767:
                return 634218767;
            case 634218772:
                return 634218772;
            case 634218786:
                return 634218786;
            case 634218792:
                return 634218792;
            case 634218795:
                return 634218795;
            default:
                return -1;
        }
    }

    @Test
    public void run7() throws Throwable {
        runTest("test7", 0); // zero
        runTest("test7", 634218695); // bellow
        runTest("test7", 634218696); // first
        runTest("test7", 634218749); // middle
        runTest("test7", 634218795); // last
        runTest("test7", 634218796); // above
        runTest("test7", 634218697); // miss
    }

    // Hasher[function=val >> min, effort=2, cardinality=16]
    public static int test8(int arg) {
        switch (arg) {
            case 473982403:
                return 473982403;
            case 473982413:
                return 473982413;
            case 473982416:
                return 473982416;
            case 473982425:
                return 473982425;
            case 473982439:
                return 473982439;
            case 473982445:
                return 473982445;
            case 473982459:
                return 473982459;
            case 473982468:
                return 473982468;
            case 473982479:
                return 473982479;
            case 473982482:
                return 473982482;
            case 473982494:
                return 473982494;
            case 473982501:
                return 473982501;
            case 473982505:
                return 473982505;
            case 473982519:
                return 473982519;
            case 473982523:
                return 473982523;
            default:
                return -1;
        }
    }

    @Test
    public void run8() throws Throwable {
        runTest("test8", 0); // zero
        runTest("test8", 473982402); // bellow
        runTest("test8", 473982403); // first
        runTest("test8", 473982468); // middle
        runTest("test8", 473982523); // last
        runTest("test8", 473982524); // above
        runTest("test8", 473982404); // miss
    }

    // Hasher[function=val >> (val & min), effort=3, cardinality=16]
    public static int test9(int arg) {
        switch (arg) {
            case 15745090:
                return 15745090;
            case 15745093:
                return 15745093;
            case 15745102:
                return 15745102;
            case 15745108:
                return 15745108;
            case 15745122:
                return 15745122;
            case 15745131:
                return 15745131;
            case 15745132:
                return 15745132;
            case 15745146:
                return 15745146;
            case 15745151:
                return 15745151;
            case 15745163:
                return 15745163;
            case 15745169:
                return 15745169;
            case 15745182:
                return 15745182;
            case 15745191:
                return 15745191;
            case 15745198:
                return 15745198;
            case 15745207:
                return 15745207;
            default:
                return -1;
        }
    }

    @Test
    public void run9() throws Throwable {
        runTest("test9", 0); // zero
        runTest("test9", 15745089); // bellow
        runTest("test9", 15745090); // first
        runTest("test9", 15745146); // middle
        runTest("test9", 15745207); // last
        runTest("test9", 15745208); // above
        runTest("test9", 15745091); // miss
    }

    // Hasher[function=(val >> min) * val, effort=4, cardinality=28]
    public static int test10(int arg) {
        switch (arg) {
            case 989358996:
                return 989358996;
            case 989359010:
                return 989359010;
            case 989359022:
                return 989359022;
            case 989359030:
                return 989359030;
            case 989359038:
                return 989359038;
            case 989359047:
                return 989359047;
            case 989359053:
                return 989359053;
            case 989359059:
                return 989359059;
            case 989359061:
                return 989359061;
            case 989359072:
                return 989359072;
            case 989359073:
                return 989359073;
            case 989359087:
                return 989359087;
            case 989359097:
                return 989359097;
            case 989359099:
                return 989359099;
            case 989359108:
                return 989359108;
            default:
                return -1;
        }
    }

    @Test
    public void run10() throws Throwable {
        runTest("test10", 0); // zero
        runTest("test10", 989358995); // bellow
        runTest("test10", 989358996); // first
        runTest("test10", 989359059); // middle
        runTest("test10", 989359108); // last
        runTest("test10", 989359109); // above
        runTest("test10", 989358997); // miss
    }

    public static class TestGenerator {

        private static int nextId = 0;
        private static final int size = 15;
        private static double minDensity = 0.5;

        // test code generator
        public static void main(String[] args) {

            Random r = new Random(0);
            Set<String> seen = new HashSet<>();
            Set<String> all = HashFunction.instances().stream().map(Object::toString).collect(Collectors.toSet());

            println("@Test");
            println("public void checkHashFunctionInstances() {");
            println("    List<String> coveredByTestCases = Arrays.asList(" + String.join(", ", all.stream().map(s -> "\"" + s + "\"").collect(Collectors.toSet())) + ");");
            println("    Set<String> functions = HashFunction.instances().stream().map(Object::toString).collect(Collectors.toSet());");
            println("    functions.removeAll(coveredByTestCases);");
            println("    assertTrue(\"The following hash functions are not covered by the `Switch03` test: \" + functions +");
            println("            \". Re-run the `Switch03.TestGenerator.main` and update the test class.\", functions.isEmpty());");
            println("}");

            while (seen.size() < all.size()) {
                int v = r.nextInt(Integer.MAX_VALUE / 2);
                List<Integer> keys = new ArrayList<>();
                while (keys.size() < 15) {
                    keys.add(v);
                    v += r.nextInt(15);
                }
                keys.sort(Integer::compare);
                double density = ((double) keys.size() + 1) / (keys.get(keys.size() - 1) - keys.get(0));
                if (density < minDensity) {
                    Hasher.forKeys(toConstants(keys), minDensity).ifPresent(h -> {
                        String f = h.function().toString();
                        if (!seen.contains(f)) {
                            gen(keys, h);
                            seen.add(f);
                        }
                    });
                }
            }
        }

        private static void gen(List<Integer> keys, Hasher hasher) {
            int id = ++nextId;

            println("// " + hasher + "");
            println("public static int test" + id + "(int arg) {");
            println("    switch (arg) {");

            for (Integer key : keys) {
                println("        case " + key + ": return " + key + ";");
            }

            println("        default: return -1;");
            println("    }");
            println("}");

            int miss = keys.get(0) + 1;
            while (keys.contains(miss)) {
                miss++;
            }

            println("@Test");
            println("public void run" + id + "() throws Throwable {");
            println("    runTest(\"test" + id + "\", 0); // zero ");
            println("    runTest(\"test" + id + "\", " + (keys.get(0) - 1) + "); // bellow ");
            println("    runTest(\"test" + id + "\", " + keys.get(0) + "); // first ");
            println("    runTest(\"test" + id + "\", " + keys.get(size / 2) + "); // middle ");
            println("    runTest(\"test" + id + "\", " + keys.get(size - 1) + "); // last ");
            println("    runTest(\"test" + id + "\", " + (keys.get(size - 1) + 1) + "); // above ");
            println("    runTest(\"test" + id + "\", " + miss + "); // miss ");
            println("}");
        }

        private static void println(String s) {
            System.out.println(s);
        }

        private static JavaConstant[] toConstants(List<Integer> keys) {
            JavaConstant[] ckeys = new JavaConstant[keys.size()];

            for (int i = 0; i < keys.size(); i++) {
                ckeys[i] = JavaConstant.forInt(keys.get(i));
            }
            return ckeys;
        }
    }
}
