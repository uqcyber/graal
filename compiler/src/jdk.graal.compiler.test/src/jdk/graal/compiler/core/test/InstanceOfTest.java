/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Test;

public class InstanceOfTest extends GraalCompilerTest {

    // TODO(jm):
    //  This test appears to fails because the translator attempts to
    //  resolve the String<init> method within the Object class.
    //  Maybe start with test6 and test7 and come back to this one.
    public static boolean instanceOfSnippet(boolean which) {
        Object o = which ? new Object() : new String();
        return o instanceof String;
    }

    @Test
    public void test0() {
        test("instanceOfSnippet", false);
    }

    @Test
    public void test1() {
        test("instanceOfSnippet", true);
    }

    public static boolean instanceOfSnippet2(Object arg) {
        return arg instanceof String;
    }

    @Test
    public void test2() {
        test("instanceOfSnippet2", new Object());
    }

    @Test
    public void test3() {
        test("instanceOfSnippet2", new String());
    }

    public static class A {
    }

    public static class B extends A {
    }

    public static boolean instanceOfSnippet3(Object arg) {
        return arg instanceof B;
    }

    @Test
    public void test4() {
        test("instanceOfSnippet3", new A());
    }

    @Test
    public void test5() {
        test("instanceOfSnippet3", new B());
    }

    public static boolean instanceOfSnippet4(boolean which) {
        Object o = which ? new A() : new B();
        return o instanceof B;
    }

    @Test
    public void test6() {
        test("instanceOfSnippet4", true);
    }

    @Test
    public void test7() {
        test("instanceOfSnippet4", false);
    }
}
