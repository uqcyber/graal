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
package jdk.graal.compiler.truffle.phases;

import java.util.ArrayList;

import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.graal.compiler.truffle.nodes.TrufflePreserveFrameStateNode;

/**
 * Removes redundant Truffle preserve-frame-state markers introduced by inlining during PE.
 *
 * A marker is redundant if it is dominated by another marker. In each block, only the first marker
 * is considered a candidate keeper.
 */
public final class TrufflePreserveFrameStateCleanupPhase extends BasePhase<TruffleTierContext> {

    @Override
    protected void run(StructuredGraph graph, TruffleTierContext context) {
        graph.checkCancellation();
        if (graph.getNodes(TrufflePreserveFrameStateNode.TYPE).count() <= 1) {
            return;
        }
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeDominators(true).build();
        cfg.visitDominatorTreeDefault(new RedundantPreserveNodeCleanupVisitor(graph));
    }

    private static final class RedundantPreserveNodeCleanupVisitor implements ControlFlowGraph.RecursiveVisitor<Boolean> {
        private final StructuredGraph graph;
        private boolean markerSeenOnCurrentPath;

        private RedundantPreserveNodeCleanupVisitor(StructuredGraph graph) {
            this.graph = graph;
            this.markerSeenOnCurrentPath = false;
        }

        @Override
        public Boolean enter(HIRBlock block) {
            boolean previousMarkerSeenOnPath = markerSeenOnCurrentPath;
            boolean keepFirstMarkerInBlock = !markerSeenOnCurrentPath;
            boolean keptMarkerInBlock = false;
            for (TrufflePreserveFrameStateNode marker : collectMarkers(block)) {
                if (!marker.isAlive()) {
                    continue;
                }
                if (keepFirstMarkerInBlock && !keptMarkerInBlock) {
                    keptMarkerInBlock = true;
                    markerSeenOnCurrentPath = true;
                } else {
                    graph.removeFixed(marker);
                }
            }
            return previousMarkerSeenOnPath;
        }

        @Override
        public void exit(HIRBlock block, Boolean previousMarkerSeenOnPath) {
            markerSeenOnCurrentPath = Boolean.TRUE.equals(previousMarkerSeenOnPath);
        }
    }

    private static ArrayList<TrufflePreserveFrameStateNode> collectMarkers(HIRBlock block) {
        ArrayList<TrufflePreserveFrameStateNode> markers = new ArrayList<>(2);
        for (FixedNode node : block.getNodes()) {
            if (node instanceof TrufflePreserveFrameStateNode marker && marker.isAlive()) {
                markers.add(marker);
            }
        }
        return markers;
    }
}
