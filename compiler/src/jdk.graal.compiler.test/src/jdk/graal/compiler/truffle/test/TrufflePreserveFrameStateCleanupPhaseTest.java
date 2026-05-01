/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.truffle.nodes.TrufflePreserveFrameStateNode;
import jdk.graal.compiler.truffle.phases.TrufflePreserveFrameStateCleanupPhase;

public class TrufflePreserveFrameStateCleanupPhaseTest extends TruffleCompilerImplTest {

    @Test
    public void testDominatedMarkersAreRemoved() {
        StructuredGraph graph = parseGraph("snippetIfElse", boolean.class);

        insertEntryMarker(graph);
        insertMarkersBeforeReturns(graph);
        Assert.assertEquals(3L, countMarkers(graph));

        applyCleanup(graph);

        Assert.assertEquals(1L, countMarkers(graph));
    }

    @Test
    public void testIndependentBranchMarkersAreKept() {
        StructuredGraph graph = parseGraph("snippetIfElse", boolean.class);

        insertMarkersBeforeReturns(graph);
        Assert.assertEquals(2L, countMarkers(graph));

        applyCleanup(graph);

        Assert.assertEquals(2L, countMarkers(graph));
    }

    @Test
    public void testOnlyFirstMarkerInBlockIsKept() {
        StructuredGraph graph = parseGraph("snippetStraightLine", int.class);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        Assert.assertNotNull(returnNode);

        insertMarkerBefore(graph, returnNode);
        insertMarkerBefore(graph, returnNode);
        Assert.assertEquals(2L, countMarkers(graph));

        applyCleanup(graph);

        Assert.assertEquals(1L, countMarkers(graph));
    }

    @Test
    public void testMarkersAreRemovedAfterFSA() {
        StructuredGraph graph = parseGraph("snippetStraightLine", int.class);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        Assert.assertNotNull(returnNode);

        insertMarkerBefore(graph, returnNode);
        Assert.assertEquals(1L, countMarkers(graph));

        graph.getGraphState().setAfterFSA();
        CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());

        Assert.assertEquals(0L, countMarkers(graph));
    }

    private StructuredGraph parseGraph(String methodName, Class<?>... parameterTypes) {
        return parseForCompile(getResolvedJavaMethod(TrufflePreserveFrameStateCleanupPhaseTest.class, methodName, parameterTypes));
    }

    private static void applyCleanup(StructuredGraph graph) {
        new TrufflePreserveFrameStateCleanupPhase().apply(graph, null);
    }

    private static long countMarkers(StructuredGraph graph) {
        long count = 0;
        for (Node node : graph.getNodes()) {
            if (node instanceof TrufflePreserveFrameStateNode) {
                count++;
            }
        }
        return count;
    }

    private static void insertEntryMarker(StructuredGraph graph) {
        FixedNode firstFixed = graph.start().next();
        Assert.assertNotNull(firstFixed);
        insertMarkerBefore(graph, firstFixed);
    }

    private static void insertMarkersBeforeReturns(StructuredGraph graph) {
        var returns = graph.getNodes(ReturnNode.TYPE).snapshot();
        Assert.assertFalse(returns.isEmpty());
        for (ReturnNode returnNode : returns) {
            insertMarkerBefore(graph, returnNode);
        }
    }

    private static void insertMarkerBefore(StructuredGraph graph, FixedNode node) {
        TrufflePreserveFrameStateNode marker = graph.add(new TrufflePreserveFrameStateNode());
        FrameState state = graph.getNodes(FrameState.TYPE).first();
        Assert.assertNotNull(state);
        marker.setStateAfter(state);
        graph.addBeforeFixed(node, marker);
    }

    public static int snippetIfElse(boolean cond) {
        if (cond) {
            return 1;
        }
        return 2;
    }

    public static int snippetStraightLine(int value) {
        return value + 1;
    }
}
