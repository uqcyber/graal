/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class VeriOptFactorialTest extends GraalCompilerTest {

    private static int N = 5;

    @Test
    public void testFact() {
        test("fact", 5);
    }

    @SuppressWarnings("all")
    public static int fact(int n) {
        int result = 1;
        while (n > 1) {
            result = result * n;
            n = n - 1;
        }
        return result;
    }

    @Test
    public void testFactStatic() {
        test("factStatic");
    }

    public static int factStatic() {
        int result = 1;
        int n = N;
        while (n > 1) {
            result = result * n;
            n = n - 1;
        }
        return result;
    }

    @Test
    public void testFactMap() {
        test("factMap");
    }

    public static int factMap() {
        Map<Integer, Integer> map = new HashMap<>();
        int result = 1;
        int n = N;
        while (n > 1) {
            result = result * n;
            n = n - 1;
            map.put(n, result);
        }
        return map.get(1);
    }
}