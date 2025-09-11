/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.reflect.Modifier.isStatic;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;
import static jdk.graal.compiler.nodes.ConstantNode.getConstantNodes;
import static jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
import static jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
import static jdk.vm.ci.runtime.JVMCICompiler.INVOCATION_ENTRY_BCI;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableEconomicMap;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.test.veriopt.ConditionalEliminationValidation;
import jdk.graal.compiler.core.test.veriopt.VeriOptTestUtil;
import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.core.test.veriopt.VeriOptGraphCache;
import jdk.graal.compiler.core.veriopt.VeriOptGraphTranslator;
import jdk.graal.compiler.core.veriopt.VeriOptValueEncoder;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.api.test.ModuleSupport;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationPrinter;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.GraalCompiler.Request;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.phases.fuzzing.PhasePlanSerializer;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpHandler;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.hotspot.HotSpotGraphBuilderPhase;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.BreakpointNode;
import jdk.graal.compiler.nodes.Cancellable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.StructuredGraph.Builder;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.ProfileProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.OptimisticOptimizations.Optimization;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.inlining.info.InlineInfo;
import jdk.graal.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.tiers.TargetProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.graal.compiler.test.GraalTest;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;

/**
 * Base class for compiler unit tests.
 * <p>
 * White box tests for compiler transformations use this pattern:
 * <ol>
 * <li>Create a graph by {@linkplain #parseEager parsing} a method.</li>
 * <li>Manually modify the graph (e.g. replace a parameter node with a constant).</li>
 * <li>Apply a transformation to the graph.</li>
 * <li>Assert that the transformed graph is equal to an expected graph.</li>
 * </ol>
 * <p>
 * See {@link InvokeHintsTest} as an example of a white box test.
 * <p>
 * Black box tests use the {@link #test(String, Object...)} or
 * {@link #testN(int, String, Object...)} to execute some method in the interpreter and compare its
 * result against that produced by a Graal compiled version of the method.
 * <p>
 * These tests will be run by the {@code mx unittest} command.
 */
public abstract class GraalCompilerTest extends GraalTest {

    /**
     * Gets the initial option values provided by the Graal runtime. These are option values
     * typically parsed from the command line.
     */
    public static OptionValues getInitialOptions() {
        return Graal.getRequiredCapability(OptionValues.class);
    }

    private static final int BAILOUT_RETRY_LIMIT = 1;
    private final Providers providers;
    private final Backend backend;

    // Stores whether a JVMClass mapping has been created for a particular test run
    private static boolean classesEncoded = false;

    private VeriOptGraphCache veriOptGraphCache = new VeriOptGraphCache(this::veriOptGetGraph);

    /**
     * Representative class for the {@code java.base} module.
     */
    public static final Class<?> JAVA_BASE = Class.class;

    /**
     * Exports the package named {@code packageName} declared in {@code moduleMember}'s module to
     * this object's module. This must be called before accessing non-public packages.
     */
    protected final void exportPackage(Class<?> moduleMember, String packageName) {
        ModuleSupport.exportPackageTo(moduleMember, packageName, getClass());
    }

    /**
     * Denotes a test method that must be inlined by the {@link BytecodeParser}.
     */
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BytecodeParserForceInline {
    }

    /**
     * Denotes a test method that must never be inlined by the {@link BytecodeParser}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface BytecodeParserNeverInline {
        /**
         * Specifies if the call should be implemented with {@link InvokeWithExceptionNode} instead
         * of {@link InvokeNode}.
         */
        boolean invokeWithException() default false;
    }

    /**
     * Can be overridden by unit tests to verify properties of the graph.
     *
     * @param graph the graph at the end of HighTier
     * @throws AssertionError if the verification fails
     */
    protected void checkHighTierGraph(StructuredGraph graph) {
    }

    /**
     * Can be overridden by unit tests to verify properties of the graph.
     *
     * @param graph the graph at the end of MidTier
     * @throws AssertionError if the verification fails
     */
    protected void checkMidTierGraph(StructuredGraph graph) {
    }

    /**
     * Can be overridden by unit tests to verify properties of the graph.
     *
     * @param graph the graph at the end of LowTier
     * @throws AssertionError if the verification fails
     */
    protected void checkLowTierGraph(StructuredGraph graph) {
    }

    protected static void breakpoint() {
    }

    @SuppressWarnings("unused")
    protected static void breakpoint(int arg0) {
    }

    protected static void shouldBeOptimizedAway() {
    }

    protected static void safepoint() {
    }

    /**
     * Prevent canonicalization on {@code input} until after given graph {@code stage}.
     * {@code stage} must be constant for this to work.
     */
    protected static <T> T delayConstantFoldingUntil(T input, GraphState.StageFlag stage) {
        return delayConstantFoldingUntil(input, stage.ordinal());
    }

    private static <T> T delayConstantFoldingUntil(T input, @SuppressWarnings("unused") int stage) {
        return input;
    }

    protected Suites createSuites(OptionValues opts) {
        Suites ret = backend.getSuites().getDefaultSuites(opts, getTarget().arch).copy();

        String phasePlanFile = System.getProperty("test.graal.phaseplan.file");
        if (phasePlanFile != null) {
            ret = PhasePlanSerializer.loadPhasePlan(phasePlanFile, ret);
        } else {
            testPhasePlanSerialization(ret, opts);
        }

        if (getSpeculationLog() == null) {
            removeSpeculativePhases(ret);
        }

        ListIterator<BasePhase<? super HighTierContext>> iter = ret.getHighTier().findPhase(ConvertDeoptimizeToGuardPhase.class, true);
        if (iter == null) {
            /*
             * in the economy configuration, we don't have the ConvertDeoptimizeToGuard phase, so we
             * just select the first CanonicalizerPhase in HighTier
             */
            iter = ret.getHighTier().findPhase(CanonicalizerPhase.class);
        }
        ret.getHighTier().appendPhase(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                checkHighTierGraph(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            public CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        ret.getMidTier().appendPhase(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                checkMidTierGraph(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            public CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        ret.getLowTier().appendPhase(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                checkLowTierGraph(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            public CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        return ret;
    }

    private static void removeSpeculativePhases(Suites suites) {
        suites.getHighTier().removeSubTypePhases(Speculative.class);
        suites.getMidTier().removeSubTypePhases(Speculative.class);
        suites.getLowTier().removeSubTypePhases(Speculative.class);
    }

    private void testPhasePlanSerialization(Suites originalSuites, OptionValues opts) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Suites newSuites;
        try {
            try (DataOutputStream dos = new DataOutputStream(baos)) {
                PhasePlanSerializer.savePhasePlan(dos, originalSuites);
            }
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                newSuites = PhasePlanSerializer.loadPhasePlan(in, backend.getSuites().getDefaultSuites(opts, getTarget().arch).copy());
            }
        } catch (IOException e) {
            throw new GraalError(e, "Error in phase plan serialization");
        }
        Assert.assertEquals(originalSuites.getHighTier().toString(), newSuites.getHighTier().toString());
        Assert.assertEquals(originalSuites.getMidTier().toString(), newSuites.getMidTier().toString());
        Assert.assertEquals(originalSuites.getLowTier().toString(), newSuites.getLowTier().toString());

        Assert.assertEquals(originalSuites.getHighTier().getPhases(), newSuites.getHighTier().getPhases());
        Assert.assertEquals(originalSuites.getMidTier().getPhases(), newSuites.getMidTier().getPhases());
        Assert.assertEquals(originalSuites.getLowTier().getPhases(), newSuites.getLowTier().getPhases());
    }

    protected LIRSuites createLIRSuites(OptionValues opts) {
        LIRSuites ret = backend.getSuites().getDefaultLIRSuites(opts).copy();
        return ret;
    }

    protected static final ThreadLocal<HashMap<ResolvedJavaMethod, Pair<OptionValues, InstalledCode>>> cache = ThreadLocal.withInitial(HashMap::new);

    /**
     * Reset the entire {@linkplain #cache} of {@linkplain InstalledCode}. Additionally, invalidate
     * all code that was installed before. Some tests install default methods for example and one
     * test should never influence another one.
     */
    @BeforeClass
    public static void resetCodeCache() {
        for (Pair<OptionValues, InstalledCode> code : cache.get().values()) {
            code.getRight().invalidate();
        }
        cache.get().clear();
    }

    /**
     * Overwrites the current speculation log by the result from calling
     * {@code createSpeculationLog}. The speculation log is reset before each test.
     */
    @Before
    public void resetSpeculationLog() {
        this.speculationLog = createSpeculationLog();
    }

    /**
     * Resets the code cache and the speculation log.
     */
    public void resetCache() {
        resetCodeCache();
        resetSpeculationLog();
    }

    @SuppressWarnings("this-escape")
    public GraalCompilerTest() {
        this.backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        this.providers = getBackend().getProviders();
    }

    /**
     * Set up a test for a non-default backend. The test should check (via {@link #getBackend()} )
     * whether the desired backend is available.
     *
     * @param arch the name of the desired backend architecture
     */
    public GraalCompilerTest(Class<? extends Architecture> arch) {
        RuntimeProvider runtime = Graal.getRequiredCapability(RuntimeProvider.class);
        Backend b = runtime.getBackend(arch);
        if (b != null) {
            this.backend = b;
        } else {
            // Fall back to the default/host backend
            this.backend = runtime.getHostBackend();
        }
        this.providers = backend.getProviders();
    }

    /**
     * Set up a test for a non-default backend.
     *
     * @param backend the desired backend
     */
    public GraalCompilerTest(Backend backend) {
        this.backend = backend;
        this.providers = backend.getProviders();
    }

    @Override
    @After
    public void afterTest() {
        if (invocationPluginExtensions != null) {
            synchronized (this) {
                if (invocationPluginExtensions != null) {
                    extendedInvocationPlugins.removeTestPlugins(invocationPluginExtensions);
                    extendedInvocationPlugins = null;
                    invocationPluginExtensions = null;
                }
            }
        }
        super.afterTest();
    }

    /**
     * Gets a {@link DebugContext} object corresponding to {@code options}, creating a new one if
     * none currently exists. Debug contexts created by this method will have their
     * {@link DebugDumpHandler}s closed in {@link #afterTest()}.
     */
    protected DebugContext getDebugContext() {
        return getDebugContext(getInitialOptions(), null, null);
    }

    @Override
    protected Collection<DebugDumpHandlersFactory> getDebugHandlersFactories() {
        return Collections.singletonList(new GraalDebugHandlersFactory(getSnippetReflection()));
    }

    protected void assertEquals(StructuredGraph expected, StructuredGraph graph) {
        assertEquals(expected, graph, false, true);
    }

    protected int countUnusedConstants(StructuredGraph graph) {
        int total = 0;
        for (ConstantNode node : getConstantNodes(graph)) {
            if (node.hasNoUsages()) {
                total++;
            }
        }
        return total;
    }

    protected int getNodeCountExcludingUnusedConstants(StructuredGraph graph) {
        return graph.getNodeCount() - countUnusedConstants(graph);
    }

    protected void assertEquals(StructuredGraph expected, StructuredGraph actual, boolean excludeVirtual, boolean checkConstants) {
        assertEquals(expected, actual, excludeVirtual, checkConstants, false);
    }

    /**
     * Asserts that two graphs are equal.
     * <p>
     * If the {@link jdk.graal.compiler.nodes.OptimizationLog} is enabled, the logs of the "actual"
     * graph are emitted.
     *
     * @param addGraphsToDebugContext if true, a scope is opened that contains {@code expected} and
     *            {@code actual} in its context so that these graphs are dumped when the comparison
     *            fails and {@code DumpOnError=true}
     */
    protected void assertEquals(StructuredGraph expected,
                    StructuredGraph actual,
                    boolean excludeVirtual,
                    boolean checkConstants,
                    boolean addGraphsToDebugContext) {
        DebugContext debug = actual.getDebug();
        actual.getOptimizationLog().emit();

        String expectedString = getCanonicalGraphString(expected, excludeVirtual, checkConstants);
        String actualString = getCanonicalGraphString(actual, excludeVirtual, checkConstants);
        String mismatchString = compareGraphStrings(expected, expectedString, actual, actualString);

        // Open a scope so that `expected` and `actual` are dumped if DumpOnError=true
        try (DebugContext.Scope _ = addGraphsToDebugContext ? debug.scope("GraphEqualsTest", expected, actual) : null) {
            if (!excludeVirtual && getNodeCountExcludingUnusedConstants(expected) != getNodeCountExcludingUnusedConstants(actual)) {
                debug.dump(DebugContext.BASIC_LEVEL, expected, "Node count not matching - expected");
                debug.dump(DebugContext.BASIC_LEVEL, actual, "Node count not matching - actual");
                Assert.fail("Graphs do not have the same number of nodes: " + expected.getNodeCount() + " vs. " + actual.getNodeCount() + "\n" + mismatchString);
            }
            if (!expectedString.equals(actualString)) {
                debug.dump(DebugContext.BASIC_LEVEL, expected, "mismatching graphs - expected");
                debug.dump(DebugContext.BASIC_LEVEL, actual, "mismatching graphs - actual");
                Assert.fail(mismatchString);
            }
        } catch (AssertionError e) {
            if (!addGraphsToDebugContext) {
                throw e;
            }
            throw debug.handle(e);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private static String compareGraphStrings(StructuredGraph expectedGraph, String expectedString, StructuredGraph actualGraph, String actualString) {
        if (!expectedString.equals(actualString)) {
            String[] expectedLines = expectedString.split("\n");
            String[] actualLines = actualString.split("\n");
            int diffIndex = -1;
            int limit = Math.min(actualLines.length, expectedLines.length);
            String marker = " <<<";
            for (int i = 0; i < limit; i++) {
                if (!expectedLines[i].equals(actualLines[i])) {
                    diffIndex = i;
                    break;
                }
            }
            if (diffIndex == -1) {
                // Prefix is the same so add some space after the prefix
                diffIndex = limit;
                if (actualLines.length == limit) {
                    actualLines = Arrays.copyOf(actualLines, limit + 1);
                    actualLines[diffIndex] = "";
                } else {
                    assert expectedLines.length == limit;
                    expectedLines = Arrays.copyOf(expectedLines, limit + 1);
                    expectedLines[diffIndex] = "";
                }
            }
            // Place a marker next to the first line that differs
            expectedLines[diffIndex] = expectedLines[diffIndex] + marker;
            actualLines[diffIndex] = actualLines[diffIndex] + marker;
            String ediff = String.join("\n", expectedLines);
            String adiff = String.join("\n", actualLines);
            return "mismatch in graphs:\n========= expected (" + expectedGraph + ") =========\n" + ediff + "\n\n========= actual (" + actualGraph + ") =========\n" + adiff;
        } else {
            return "mismatch in graphs";
        }
    }

    public boolean areEqual(StructuredGraph expected, StructuredGraph graph) {
        return areEqual(expected, graph, false, true);
    }

    public boolean areEqual(StructuredGraph expected, StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
        String expectedString = getCanonicalGraphString(expected, excludeVirtual, checkConstants);
        String actualString = getCanonicalGraphString(graph, excludeVirtual, checkConstants);
        String mismatchString = compareGraphStrings(expected, expectedString, graph, actualString);

        if (!excludeVirtual && getNodeCountExcludingUnusedConstants(expected) != getNodeCountExcludingUnusedConstants(graph)) {
            expected.getDebug().dump(DebugContext.BASIC_LEVEL, expected, "Node count not matching - expected");
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "Node count not matching - actual");
            System.out.println("Graphs do not have the same number of nodes: " + expected.getNodeCount() + " vs. " + graph.getNodeCount() + "\n" + mismatchString);
            return false;
        }
        if (!expectedString.equals(actualString)) {
            expected.getDebug().dump(DebugContext.BASIC_LEVEL, expected, "mismatching graphs - expected");
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "mismatching graphs - actual");
            System.out.println(mismatchString);
            return false;
        }
        return true;
    }

    protected void assertOptimizedAway(StructuredGraph g) {
        Assert.assertEquals("nodes to be optimized away", 0, g.getNodes().filter(NotOptimizedNode.class).count());
    }

    protected void assertConstantReturn(StructuredGraph graph, int value) {
        String graphString = getCanonicalGraphString(graph, false, true);
        Assert.assertEquals("unexpected number of ReturnNodes: " + graphString, graph.getNodes(ReturnNode.TYPE).count(), 1);
        ValueNode result = graph.getNodes(ReturnNode.TYPE).first().result();
        Assert.assertTrue("unexpected ReturnNode result node: " + graphString, result.isConstant());
        Assert.assertEquals("unexpected ReturnNode result kind: " + graphString, result.asJavaConstant().getJavaKind(), JavaKind.Int);
        Assert.assertEquals("unexpected ReturnNode result: " + graphString, result.asJavaConstant().asInt(), value);
    }

    protected static String getCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulingStrategy.EARLIEST);

        ScheduleResult scheduleResult = graph.getLastSchedule();

        NodeMap<Integer> canonicalId = graph.createNodeMap();
        int nextId = 0;

        List<String> constantsLines = new ArrayList<>();

        StringBuilder result = new StringBuilder();
        for (HIRBlock block : scheduleResult.getCFG().getBlocks()) {
            result.append("Block ").append(block).append(' ');
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                HIRBlock succ = block.getSuccessorAt(i);
                result.append(succ).append(' ');
            }
            result.append('\n');
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                if (node instanceof ValueNode && node.isAlive()) {
                    if (!excludeVirtual || !(node instanceof VirtualObjectNode || node instanceof ProxyNode || node instanceof FullInfopointNode || node instanceof ParameterNode)) {
                        if (node instanceof ConstantNode) {
                            String name = checkConstants ? node.toString(Verbosity.Name) : node.getClass().getSimpleName();
                            if (excludeVirtual) {
                                constantsLines.add(name);
                            } else {
                                constantsLines.add(name + "    (" + filteredUsageCount(node) + ")");
                            }
                        } else {
                            int id;
                            if (canonicalId.get(node) != null) {
                                id = canonicalId.get(node);
                            } else {
                                id = nextId++;
                                canonicalId.set(node, id);
                            }
                            String name = node.getClass().getSimpleName();
                            result.append("  ").append(id).append('|').append(name);
                            if (node instanceof AccessFieldNode) {
                                result.append('#');
                                result.append(((AccessFieldNode) node).field());
                            }
                            if (!excludeVirtual) {
                                result.append("    (");
                                result.append(filteredUsageCount(node));
                                result.append(')');
                            }
                            result.append('\n');
                        }
                    }
                }
            }
        }

        StringBuilder constantsLinesResult = new StringBuilder();
        constantsLinesResult.append(constantsLines.size()).append(" constants:\n");
        Collections.sort(constantsLines);
        for (String s : constantsLines) {
            constantsLinesResult.append(s);
            constantsLinesResult.append('\n');
        }

        return constantsLinesResult.toString() + result.toString();
    }

    /**
     * @return usage count excluding {@link FrameState} usages
     */
    private static int filteredUsageCount(Node node) {
        return node.usages().filter(n -> !(n instanceof FrameState)).count();
    }

    /**
     * @param graph
     * @return a scheduled textual dump of {@code graph} .
     */
    protected static String getScheduledGraphString(StructuredGraph graph) {
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);
        ScheduleResult scheduleResult = graph.getLastSchedule();

        StringBuilder result = new StringBuilder();
        HIRBlock[] blocks = scheduleResult.getCFG().getBlocks();
        for (HIRBlock block : blocks) {
            result.append("Block ").append(block).append(' ');
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                HIRBlock succ = block.getSuccessorAt(i);
                result.append(succ).append(' ');
            }
            result.append('\n');
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                result.append(String.format("%1S\n", node));
            }
        }
        return result.toString();
    }

    protected Backend getBackend() {
        return backend;
    }

    public final Providers getProviders() {
        return providers;
    }

    /**
     * Override the {@link OptimisticOptimizations} settings used for the test. This is called for
     * all the paths where the value is set so it is the proper place for a test override. Setting
     * it in other places can result in inconsistent values being used in other parts of the
     * compiler.
     *
     * This method returns settings such that all optimizations except
     * {@link Optimization#RemoveNeverExecutedCode} are enabled. The latter is removed to reduce a
     * major source of indeterminism in tests caused by profiles. Most tests should ignore profiles
     * as they can differ wildly depending on the set of tests being run.
     */
    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.ALL.remove(Optimization.RemoveNeverExecutedCode);
    }

    protected final HighTierContext getDefaultHighTierContext() {
        return new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), getOptimisticOptimizations());
    }

    /**
     * Returns a custom high tier context with custom {@link GraphBuilderPhase}.
     */
    protected final HighTierContext getEagerHighTierContext() {
        return new HighTierContext(getProviders(),
                        getEagerGraphBuilderSuite(),
                        getOptimisticOptimizations());
    }

    protected final MidTierContext getDefaultMidTierContext() {
        return new MidTierContext(getProviders(), getTargetProvider(), getOptimisticOptimizations(), null);
    }

    protected final LowTierContext getDefaultLowTierContext() {
        return new LowTierContext(getProviders(), getTargetProvider());
    }

    protected SnippetReflectionProvider getSnippetReflection() {
        return Graal.getRequiredCapability(SnippetReflectionProvider.class);
    }

    protected TargetDescription getTarget() {
        return getTargetProvider().getTarget();
    }

    protected TargetProvider getTargetProvider() {
        return getBackend();
    }

    protected CodeCacheProvider getCodeCache() {
        return getProviders().getCodeCache();
    }

    protected ConstantReflectionProvider getConstantReflection() {
        return getProviders().getConstantReflection();
    }

    public MetaAccessProvider getMetaAccess() {
        return getProviders().getMetaAccess();
    }

    protected LoweringProvider getLowerer() {
        return getProviders().getLowerer();
    }

    protected final BasePhase<HighTierContext> createInliningPhase() {
        return createInliningPhase(this.createCanonicalizerPhase());
    }

    protected BasePhase<HighTierContext> createInliningPhase(CanonicalizerPhase canonicalizer) {
        return createInliningPhase(null, canonicalizer);
    }

    static class GreedyTestInliningPolicy extends GreedyInliningPolicy {
        GreedyTestInliningPolicy(Map<Invoke, Double> hints) {
            super(hints);
        }

        @Override
        protected int previousLowLevelGraphSize(InlineInfo info) {
            // Ignore previous compiles for tests
            return 0;
        }
    }

    protected BasePhase<HighTierContext> createInliningPhase(Map<Invoke, Double> hints, CanonicalizerPhase canonicalizer) {
        return new InliningPhase(new GreedyTestInliningPolicy(hints), canonicalizer);
    }

    protected CompilationIdentifier getCompilationId(ResolvedJavaMethod method) {
        return getBackend().getCompilationIdentifier(method);
    }

    protected CompilationIdentifier getOrCreateCompilationId(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph) {
        if (graph != null) {
            return graph.compilationId();
        }
        return getCompilationId(installedCodeOwner);
    }

    protected void testN(int n, final String name, final Object... args) {
        final List<Throwable> errors = new ArrayList<>(n);
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(i + ":" + name) {

                @Override
                public void run() {
                    try {
                        test(name, args);
                    } catch (Throwable e) {
                        errors.add(e);
                    }
                }
            };
            threads[i] = t;
            t.start();
        }
        for (int i = 0; i < n; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                errors.add(e);
            }
        }
        if (!errors.isEmpty()) {
            throw new MultiCauseAssertionError(errors.size() + " failures", errors.toArray(new Throwable[errors.size()]));
        }
    }

    protected Object referenceInvoke(ResolvedJavaMethod method, Object receiver, Object... args)
                    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        return invoke(method, receiver, args);
    }

    public static class Result {

        public final Object returnValue;
        public final Throwable exception;

        public Result(Object returnValue, Throwable exception) {
            this.returnValue = returnValue;
            this.exception = exception;
        }

        @Override
        public String toString() {
            return exception == null ? returnValue == null ? "null" : returnValue.toString() : "!" + exception;
        }
    }

    /**
     * Called before a test is executed.
     */
    protected void before(@SuppressWarnings("unused") ResolvedJavaMethod method) {
    }

    /**
     * Called after a test is executed.
     */
    protected void after() {
    }

    /**
     * Sets the value of the classesEncoded flag to indicate whether classes have been encoded for the current test
     * run.
     *
     * @param encoded True if classes have been encoded, else False.
     * */
    public static void setClassesEncoded(boolean encoded) {
        classesEncoded = encoded;
    }

    /**
     * Performs a range of setup actions which must occur before each individual test run.
     * */
    private static void performPreTestSetup() {
        VeriOptGraphTranslator.clearClasses();         // Ensure only classes in this test are encoded as JVMClasses.
        VeriOptGraphTranslator.clearCallableMethods(); // Ensure only methods in this test are given IRGraphs.
        setClassesEncoded(false);                      // Mark the class encoding mapping as empty.
    }

    protected Result executeExpected(ResolvedJavaMethod method, Object receiver, Object... args) {
        Result result = null;
        before(method);
        try {
            // This gives us both the expected return value as well as ensuring that the method to
            // be compiled is fully resolved
            result = new Result(referenceInvoke(method, receiver, args), null);
        } catch (InvocationTargetException e) {
            result = new Result(null, e.getTargetException());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            after();
        }

        if (VeriOpt.DUMP_TESTS) {
            String testName = getClass().getSimpleName() + "_" + method.getName();
            if (VeriOpt.DEBUG) {
                System.out.printf("\n\nDEBUG: testName=%s -> %s in class %s\n", testName, result, method.getDeclaringClass().getName());
            }
            performPreTestSetup();

            // Get the static fields for the test class, and any of its declared classes.
            VeriOptFields fields = new VeriOptFields();
            fields.getStaticClassFields(getClassDeclarationList());
            dumpTest(testName, method, result, fields, args);
        }
        if (VeriOpt.DUMP_OPTIMIZATIONS) {
            ConditionalEliminationValidation.exportConditionalElimination(this, this.getClass().getSimpleName(), method.getName());
        }
        return result;
    }

    protected Result executeActual(ResolvedJavaMethod method, Object receiver, Object... args) {
        return executeActual(getInitialOptions(), method, receiver, args);
    }

    protected Result executeActual(OptionValues options, ResolvedJavaMethod method, Object receiver, Object... args) {
        before(method);
        Object[] executeArgs = argsWithReceiver(receiver, args);

        checkArgs(method, executeArgs);

        InstalledCode compiledMethod = getCode(method, options);
        try {
            Result result = new Result(compiledMethod.executeVarargs(executeArgs), null);
            // dumpTest(getClass().getSimpleName() + "_" + method.getName() + "_actual", method,
            // result, args);
            return result;
        } catch (Throwable e) {
            return new Result(null, e);
        } finally {
            after();
        }
    }

    protected void checkArgs(ResolvedJavaMethod method, Object[] args) {
        JavaType[] sig = method.toParameterTypes();
        Assert.assertEquals(sig.length, args.length);
        for (int i = 0; i < args.length; i++) {
            JavaType javaType = sig[i];
            JavaKind kind = javaType.getJavaKind();
            Object arg = args[i];
            if (kind == JavaKind.Object) {
                if (arg != null && javaType instanceof ResolvedJavaType) {
                    ResolvedJavaType resolvedJavaType = (ResolvedJavaType) javaType;
                    Assert.assertTrue(resolvedJavaType + " from " + getMetaAccess().lookupJavaType(arg.getClass()), resolvedJavaType.isAssignableFrom(getMetaAccess().lookupJavaType(arg.getClass())));
                }
            } else {
                Assert.assertNotNull(arg);
                Assert.assertEquals(kind.toBoxedJavaClass(), arg.getClass());
            }
        }
    }

    /**
     * Prepends a non-null receiver argument to a given list or args.
     *
     * @param receiver the receiver argument to prepend if it is non-null
     */
    protected Object[] argsWithReceiver(Object receiver, Object... args) {
        Object[] executeArgs;
        if (receiver == null) {
            executeArgs = args;
        } else {
            executeArgs = new Object[args.length + 1];
            executeArgs[0] = receiver;
            for (int i = 0; i < args.length; i++) {
                executeArgs[i + 1] = args[i];
            }
        }
        return applyArgSuppliers(executeArgs);
    }

    protected final Result test(String name, Object... args) {
        return test(getInitialOptions(), name, args);
    }

    protected final Result test(OptionValues options, String name, Object... args) {
        try {
            ResolvedJavaMethod method = getResolvedJavaMethod(name);
            Object receiver = method.isStatic() ? null : this;
            return test(options, method, receiver, args);
        } catch (AssumptionViolatedException e) {
            // Suppress so that subsequent calls to this method within the
            // same Junit @Test annotated method can proceed.
            return null;
        }
    }

    protected final Result test(OptionValues options, Set<DeoptimizationReason> shouldNotDeopt, String name, Object... args) {
        try {
            ResolvedJavaMethod method = getResolvedJavaMethod(name);
            Object receiver = method.isStatic() ? null : this;
            Result expect = executeExpected(method, receiver, args);
            testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
            return expect;
        } catch (AssumptionViolatedException e) {
            // Suppress so that subsequent calls to this method within the
            // same Junit @Test annotated method can proceed.
            return null;
        }
    }

    /**
     * Type denoting a lambda that supplies a fresh value each time it is called. This is useful
     * when supplying an argument to {@link GraalCompilerTest#test(String, Object...)} where the
     * test modifies the state of the argument (e.g., updates a field).
     */
    @FunctionalInterface
    public interface ArgSupplier extends Supplier<Object> {
    }

    /**
     * Convenience method for using an {@link ArgSupplier} lambda in a varargs list.
     */
    public static Object supply(ArgSupplier supplier) {
        return supplier;
    }

    protected Result test(ResolvedJavaMethod method, Object receiver, Object... args) {
        return test(getInitialOptions(), method, receiver, args);
    }

    protected Result test(OptionValues options, ResolvedJavaMethod method, Object receiver, Object... args) {
        Result expect = executeExpected(method, receiver, args);
        if (getCodeCache() != null) {
            testAgainstExpected(options, method, expect, CollectionsUtil.setOf(), receiver, args);
        }
        return expect;
    }

    /**
     * Process a given set of arguments, converting any {@link ArgSupplier} argument to the argument
     * it supplies.
     */
    protected Object[] applyArgSuppliers(Object... args) {
        Object[] res = args;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ArgSupplier) {
                if (res == args) {
                    res = args.clone();
                }
                res[i] = ((ArgSupplier) args[i]).get();
            }
        }
        return res;
    }

    protected final void testAgainstExpected(ResolvedJavaMethod method, Result expect, Object receiver, Object... args) {
        testAgainstExpected(getInitialOptions(), method, expect, CollectionsUtil.setOf(), receiver, args);
    }

    protected final void testAgainstExpected(OptionValues options, ResolvedJavaMethod method, Result expect, Object receiver, Object... args) {
        testAgainstExpected(options, method, expect, CollectionsUtil.setOf(), receiver, args);
    }

    protected void testAgainstExpected(OptionValues options, ResolvedJavaMethod method, Result expect, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        Result actual = executeActualCheckDeopt(options, method, shouldNotDeopt, receiver, args);
        assertEquals(expect, actual);
    }

    protected final Result executeActualCheckDeopt(OptionValues options, ResolvedJavaMethod method, Set<DeoptimizationReason> shouldNotDeopt, Object receiver,
                    Object... args) {
        Map<DeoptimizationReason, Integer> deoptCounts = new EnumMap<>(DeoptimizationReason.class);
        ProfilingInfo profile = method.getProfilingInfo();
        for (DeoptimizationReason reason : shouldNotDeopt) {
            deoptCounts.put(reason, profile.getDeoptimizationCount(reason));
        }
        Result actual = executeActual(options, method, receiver, args);
        profile = method.getProfilingInfo(); // profile can change after execution
        for (DeoptimizationReason reason : shouldNotDeopt) {
            Assert.assertEquals("wrong number of deopt counts for " + reason, (int) deoptCounts.get(reason), profile.getDeoptimizationCount(reason));
        }
        return actual;
    }

    private static final List<Class<?>> C2_OMIT_STACK_TRACE_IN_FAST_THROW_EXCEPTIONS = Arrays.asList(
                    ArithmeticException.class,
                    ArrayIndexOutOfBoundsException.class,
                    ArrayStoreException.class,
                    ClassCastException.class,
                    NullPointerException.class);

    protected void assertEquals(Result expect, Result actual) {
        if (expect.exception != null) {
            Assert.assertTrue("expected " + expect.exception, actual.exception != null);
            Assert.assertEquals("Exception class", expect.exception.getClass(), actual.exception.getClass());
            // C2 can optimize out the stack trace and message in some cases
            if (!C2_OMIT_STACK_TRACE_IN_FAST_THROW_EXCEPTIONS.contains(expect.exception.getClass())) {
                Assert.assertEquals("Exception message", expect.exception.getMessage(), actual.exception.getMessage());
            }
        } else {
            if (actual.exception != null) {
                throw new AssertionError("expected " + expect.returnValue + " but got an exception", actual.exception);
            }
            assertDeepEquals(expect.returnValue, actual.returnValue);
        }
    }

    /**
     * Gets installed code for a given method, compiling it first if necessary. The graph is parsed
     * {@link #parseEager eagerly}.
     */
    protected final InstalledCode getCode(ResolvedJavaMethod method) {
        return getCode(method, null, false, false, getInitialOptions());
    }

    protected final InstalledCode getCode(ResolvedJavaMethod method, OptionValues options) {
        return getCode(method, null, false, false, options);
    }

    /**
     * Gets installed code for a given method, compiling it first if necessary.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled. If null, a graph will be obtained from
     *            {@code installedCodeOwner} via {@link #parseForCompile(ResolvedJavaMethod)}.
     */
    protected final InstalledCode getCode(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph) {
        return getCode(installedCodeOwner, graph, false, false, graph == null ? getInitialOptions() : graph.getOptions());
    }

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled. If null, a graph will be obtained from
     *            {@code installedCodeOwner} via {@link #parseForCompile(ResolvedJavaMethod)}.
     * @param forceCompile specifies whether to ignore any previous code cached for the (method,
     *            key) pair
     */
    protected final InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile) {
        return getCode(installedCodeOwner, graph, forceCompile, false, graph == null ? getInitialOptions() : graph.getOptions());
    }

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled. If null, a graph will be obtained from
     *            {@code installedCodeOwner} via {@link #parseForCompile(ResolvedJavaMethod)}.
     * @param forceCompile specifies whether to ignore any previous code cached for the (method,
     *            key) pair
     * @param installAsDefault specifies whether to install as the default implementation
     * @param options the options that will be used in {@link #parseForCompile(ResolvedJavaMethod)}
     */
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        boolean useCache = !forceCompile && getArgumentToBind() == null;
        if (useCache && graph == null) {
            HashMap<ResolvedJavaMethod, Pair<OptionValues, InstalledCode>> tlCache = cache.get();
            Pair<OptionValues, InstalledCode> cached = tlCache.get(installedCodeOwner);
            if (cached != null) {
                // Reuse the cached code if it is still valid and the same options was used for
                // the compilation. We use a deep equals for the option values to catch cases where
                // users create new option values but with the same values.
                if (cached.getRight().isValid() && (options.getMap().equals(cached.getLeft().getMap()) || optionsMapDeepEquals(options.getMap(), cached.getLeft().getMap()))) {
                    return cached.getRight();
                } else {
                    tlCache.remove(installedCodeOwner);
                }
            }
        }
        // loop for retrying compilation
        for (int retry = 0; retry <= BAILOUT_RETRY_LIMIT; retry++) {
            final CompilationIdentifier id = getOrCreateCompilationId(installedCodeOwner, graph);

            InstalledCode installedCode = null;
            StructuredGraph graphToCompile = graph == null ? parseForCompile(installedCodeOwner, id, options) : graph;
            DebugContext debug = graphToCompile.getDebug();

            try (AllocSpy _ = AllocSpy.open(installedCodeOwner); DebugContext.Scope _ = debug.scope("Compiling", graph)) {
                CompilationPrinter printer = CompilationPrinter.begin(options, id, installedCodeOwner, INVOCATION_ENTRY_BCI);
                CompilationResult compResult = compile(installedCodeOwner, graphToCompile, new CompilationResult(graphToCompile.compilationId()), id, options);

                try (DebugContext.Scope _ = debug.scope("CodeInstall", getCodeCache(), installedCodeOwner, compResult);
                                DebugContext.Activation _ = debug.activate()) {
                    try {
                        if (installAsDefault) {
                            installedCode = addDefaultMethod(debug, installedCodeOwner, compResult);
                        } else {
                            installedCode = addMethod(debug, installedCodeOwner, compResult);
                        }
                        if (installedCode == null) {
                            throw new GraalError("Could not install code for " + installedCodeOwner.format("%H.%n(%p)"));
                        }
                    } catch (BailoutException e) {
                        if (retry < BAILOUT_RETRY_LIMIT && graph == null && !e.isPermanent()) {
                            // retry (if there is no predefined graph)
                            TTY.println(String.format("Restart compilation %s (%s) due to a non-permanent bailout!", installedCodeOwner, id));
                            continue;
                        }
                        throw e;
                    }
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
                printer.finish(compResult, installedCode);

            } catch (Throwable e) {
                throw debug.handle(e);
            }
            if (useCache) {
                cache.get().put(installedCodeOwner, Pair.create(options, installedCode));
            }
            return installedCode;
        }
        throw GraalError.shouldNotReachHere("Bailout limit reached"); // ExcludeFromJacocoGeneratedReport
    }

    private static boolean optionsMapDeepEquals(UnmodifiableEconomicMap<OptionKey<?>, Object> map1, UnmodifiableEconomicMap<OptionKey<?>, Object> map2) {
        if (map1.size() != map2.size()) {
            return false;
        }
        var c1 = map1.getEntries();
        var c2 = map2.getEntries();
        while (c1.advance() && c2.advance()) {
            Object c1Key = c1.getKey();
            Object c2Key = c2.getKey();
            if (!c1Key.equals(c2Key)) {
                return false;
            }
            Object c1Val = c1.getValue();
            Object c2Val = c2.getValue();
            if (!c1Val.equals(c2Val)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Used to produce a graph for a method about to be compiled by
     * {@link #compile(ResolvedJavaMethod, StructuredGraph)} if the second parameter to that method
     * is null.
     * <p>
     * The default implementation in {@link GraalCompilerTest} is to call {@link #parseEager}.
     */
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, OptionValues options) {
        return parseEager(method, AllowAssumptions.YES, getCompilationId(method), options);
    }

    protected final StructuredGraph parseForCompile(ResolvedJavaMethod method, DebugContext debug) {
        return parseEager(method, AllowAssumptions.YES, debug);
    }

    protected final StructuredGraph parseForCompile(ResolvedJavaMethod method) {
        return parseEager(method, AllowAssumptions.YES, getCompilationId(method), getInitialOptions());
    }

    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        return parseEager(method, AllowAssumptions.YES, compilationId, options);
    }

    /**
     * Set {@code stableDimension} of all array constants in the graph to {@code 1}.
     */
    protected StructuredGraph makeAllArraysStable(StructuredGraph graph) {
        for (ConstantNode constantNode : graph.getNodes().filter(ConstantNode.class).snapshot()) {
            if (getConstantReflection().readArrayLength(constantNode.asJavaConstant()) != null && constantNode.getStableDimension() < 1) {
                ConstantNode newConstantNode = graph.unique(ConstantNode.forConstant(constantNode.asJavaConstant(), 1, true, getMetaAccess()));
                constantNode.replaceAndDelete(newConstantNode);
            }
        }
        return graph;
    }

    /**
     * Compiles a Java method.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be compiled
     */
    protected final CompilationResult compile(String methodName) {
        return compile(getResolvedJavaMethod(methodName), null);
    }

    /**
     * Compiles a given method.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled for {@code installedCodeOwner}. If null, a graph will
     *            be obtained from {@code installedCodeOwner} via
     *            {@link #parseForCompile(ResolvedJavaMethod)}.
     */
    protected final CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph) {
        OptionValues options = graph == null ? getInitialOptions() : graph.getOptions();
        CompilationIdentifier compilationId = getOrCreateCompilationId(installedCodeOwner, graph);
        return compile(installedCodeOwner, graph, new CompilationResult(compilationId), compilationId, options);
    }

    protected final CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationIdentifier compilationId) {
        OptionValues options = graph == null ? getInitialOptions() : graph.getOptions();
        return compile(installedCodeOwner, graph, new CompilationResult(compilationId), compilationId, options);
    }

    protected final CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, OptionValues options) {
        assert graph == null || graph.getOptions() == options;
        CompilationIdentifier compilationId = getOrCreateCompilationId(installedCodeOwner, graph);
        return compile(installedCodeOwner, graph, new CompilationResult(compilationId), compilationId, options);
    }

    /**
     * Compiles a given method.
     * <p>
     * Emits the {@link jdk.graal.compiler.nodes.OptimizationLog} of the compilation if the log is
     * enabled.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled for {@code installedCodeOwner}. If null, a graph will
     *            be obtained from {@code installedCodeOwner} via
     *            {@link #parseForCompile(ResolvedJavaMethod)}.
     * @param compilationId
     */
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        StructuredGraph graphToCompile = graph == null ? parseForCompile(installedCodeOwner, compilationId, options) : graph;
        lastCompiledGraph = graphToCompile;
        DebugContext debug = graphToCompile.getDebug();
        try (DebugContext.Scope _ = debug.scope("Compile", graphToCompile)) {
            assert options != null;

            Suites suites = createSuites(options);
            if (graphToCompile.getSpeculationLog() == null) {
                removeSpeculativePhases(suites);
            }

            Request<CompilationResult> request = new Request<>(graphToCompile, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), getOptimisticOptimizations(),
                            graphToCompile.getProfilingInfo(), suites, createLIRSuites(options), compilationResult, CompilationResultBuilderFactory.Default, null, null, true);
            CompilationResult result = GraalCompiler.compile(request);
            graphToCompile.getOptimizationLog().emit();
            return result;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected StructuredGraph getFinalGraph(String method) {
        return getFinalGraph(getResolvedJavaMethod(method));
    }

    protected StructuredGraph getFinalGraph(ResolvedJavaMethod method) {
        StructuredGraph graph = parseForCompile(method);
        applyFrontEnd(graph);
        return graph;
    }

    protected StructuredGraph getFinalGraph(ResolvedJavaMethod method, OptionValues options) {
        StructuredGraph graph = parseForCompile(method, options);
        applyFrontEnd(graph);
        return graph;
    }

    protected void applyFrontEnd(StructuredGraph graph) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope _ = debug.scope("FrontEnd", graph)) {
            GraalCompiler.emitFrontEnd(getProviders(), getBackend(), graph, getDefaultGraphBuilderSuite(), getOptimisticOptimizations(), graph.getProfilingInfo(), createSuites(graph.getOptions()));
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected StructuredGraph lastCompiledGraph;

    private SpeculationLog speculationLog;

    /**
     * Updates and returns the speculation log for this test. It may be called multiple times before
     * a compilation is started. Per default, each test has its own speculation log. Use
     * {@link GraalCompilerTest#resetSpeculationLog} in tests which perform multiple compilations to
     * avoid side effects between compilations.
     */
    protected SpeculationLog getSpeculationLog() {
        if (speculationLog != null) {
            speculationLog.collectFailedSpeculations();
        }
        return speculationLog;
    }

    protected SpeculationLog createSpeculationLog() {
        return getCodeCache().createSpeculationLog();
    }

    protected InstalledCode addMethod(DebugContext debug, final ResolvedJavaMethod method, final CompilationResult compilationResult) {
        return backend.createInstalledCode(debug, method, null, compilationResult, null, false, true, null);
    }

    protected InstalledCode addDefaultMethod(DebugContext debug, final ResolvedJavaMethod method, final CompilationResult compilationResult) {
        return backend.createDefaultInstalledCode(debug, method, compilationResult);
    }

    private final Map<ResolvedJavaMethod, Executable> methodMap = new ConcurrentHashMap<>();

    /**
     * Converts a reflection {@link Method} to a {@link ResolvedJavaMethod}.
     */
    protected ResolvedJavaMethod asResolvedJavaMethod(Executable method) {
        ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(method);
        methodMap.put(javaMethod, method);
        return javaMethod;
    }

    protected ResolvedJavaMethod getResolvedJavaMethod(String methodName) {
        return asResolvedJavaMethod(getMethod(methodName));
    }

    protected ResolvedJavaMethod getResolvedJavaMethod(Class<?> clazz, String methodName) {
        return asResolvedJavaMethod(getMethod(clazz, methodName));
    }

    protected ResolvedJavaMethod getResolvedJavaMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        return asResolvedJavaMethod(getMethod(clazz, methodName, parameterTypes));
    }

    protected ResolvedJavaMethod getResolvedJavaMethod(Class<?> clazz, Class<?> returnType, String methodName, Class<?>... parameterTypes) {
        return asResolvedJavaMethod(getMethod(clazz, returnType, methodName, parameterTypes));
    }

    /**
     * Gets the reflection {@link Method} from which a given {@link ResolvedJavaMethod} was created
     * or null if {@code javaMethod} does not correspond to a reflection method.
     */
    protected Executable lookupMethod(ResolvedJavaMethod javaMethod) {
        return methodMap.get(javaMethod);
    }

    @SuppressWarnings("deprecation")
    protected Object invoke(ResolvedJavaMethod javaMethod, Object receiver, Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        Executable method = lookupMethod(javaMethod);
        Assert.assertTrue(method != null);
        if (!method.isAccessible()) {
            method.setAccessible(true);
        }
        if (method instanceof Method) {
            return ((Method) method).invoke(receiver, applyArgSuppliers(args));
        }
        assert receiver == null : "no receiver for constructor invokes";
        return ((Constructor<?>) method).newInstance(applyArgSuppliers(args));
    }

    protected static Object executeVarargsSafe(InstalledCode code, Object... args) {
        try {
            return code.executeVarargs(args);
        } catch (InvalidInstalledCodeException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object invokeSafe(ResolvedJavaMethod method, Object receiver, Object... args) {
        try {
            return invoke(method, receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses a Java method in {@linkplain GraphBuilderConfiguration#getDefault default} mode to
     * produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     */
    protected final StructuredGraph parseProfiled(String methodName, AllowAssumptions allowAssumptions) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        return parse(builder(method, allowAssumptions), getDefaultGraphBuilderSuite());
    }

    /**
     * Parses a Java method in {@linkplain GraphBuilderConfiguration#getDefault default} mode to
     * produce a graph.
     *
     * @param method the method to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     */
    protected final StructuredGraph parseProfiled(ResolvedJavaMethod method, AllowAssumptions allowAssumptions) {
        return parse(builder(method, allowAssumptions), getDefaultGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     */
    public final StructuredGraph parseEager(String methodName, AllowAssumptions allowAssumptions) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        return parse(builder(method, allowAssumptions), getEagerGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     * @param options the option values to be used when compiling the graph
     */
    protected final StructuredGraph parseEager(String methodName, AllowAssumptions allowAssumptions, OptionValues options) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        return parse(builder(method, allowAssumptions, options), getEagerGraphBuilderSuite());
    }

    protected final StructuredGraph parseEager(String methodName, AllowAssumptions allowAssumptions, DebugContext debug) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        return parse(builder(method, allowAssumptions, debug), getEagerGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param method the method to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     */
    protected final StructuredGraph parseEager(ResolvedJavaMethod method, AllowAssumptions allowAssumptions) {
        return parse(builder(method, allowAssumptions), getEagerGraphBuilderSuite());
    }

    protected final StructuredGraph parseEager(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, DebugContext debug) {
        return parse(builder(method, allowAssumptions, debug), getEagerGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param method the method to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     * @param options the option values to be used when compiling the graph
     */
    protected final StructuredGraph parseEager(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, OptionValues options) {
        return parse(builder(method, allowAssumptions, options), getEagerGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param method the method to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     * @param compilationId the compilation identifier to be associated with the graph
     * @param options the option values to be used when compiling the graph
     */
    protected final StructuredGraph parseEager(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, OptionValues options) {
        return parse(builder(method, allowAssumptions, compilationId, options), getEagerGraphBuilderSuite());
    }

    protected final Builder builder(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, DebugContext debug) {
        OptionValues options = debug.getOptions();
        return new Builder(options, debug, allowAssumptions).method(method).compilationId(getCompilationId(method));
    }

    protected final Builder builder(ResolvedJavaMethod method, AllowAssumptions allowAssumptions) {
        OptionValues options = getInitialOptions();
        return new Builder(options, getDebugContext(options, null, method), allowAssumptions).method(method).compilationId(getCompilationId(method));
    }

    protected final Builder builder(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, OptionValues options) {
        return new Builder(options, getDebugContext(options, compilationId.toString(CompilationIdentifier.Verbosity.ID), method), allowAssumptions).method(method).compilationId(
                        compilationId);
    }

    protected final Builder builder(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, OptionValues options) {
        return new Builder(options, getDebugContext(options, null, method), allowAssumptions).method(method).compilationId(getCompilationId(method));
    }

    protected PhaseSuite<HighTierContext> getDebugGraphBuilderSuite() {
        return getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withFullInfopoints(true));
    }

    protected CompilationIdentifier createCompilationId() {
        return null;
    }

    protected ProfileProvider getProfileProvider(@SuppressWarnings("unused") ResolvedJavaMethod method) {
        return null;
    }

    /**
     * Profile provider that can be used in unit tests to avoid instabilities of the VM's profiling
     * machinery. Will return a {@link DefaultProfilingInfo} for each method.
     */
    public static final ProfileProvider NO_PROFILE_PROVIDER = new ProfileProvider() {

        @Override
        public ProfilingInfo getProfilingInfo(ResolvedJavaMethod method) {
            return getProfilingInfo(method, true, true);
        }

        @Override
        public ProfilingInfo getProfilingInfo(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR) {
            return DefaultProfilingInfo.get(TriState.FALSE);
        }

    };

    protected StructuredGraph parse(StructuredGraph.Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        ResolvedJavaMethod javaMethod = builder.getMethod();
        builder.speculationLog(getSpeculationLog());
        if (builder.getCancellable() == null) {
            builder.cancellable(getCancellable(javaMethod));
        }
        ProfileProvider differentProfileProvider = getProfileProvider(javaMethod);
        if (differentProfileProvider != null) {
            builder.profileProvider(differentProfileProvider);
        }
        CompilationIdentifier id = createCompilationId();
        if (id != null) {
            builder.compilationId(id);
        }
        assert javaMethod.getAnnotation(Test.class) == null : "shouldn't parse method with @Test annotation: " + javaMethod;
        StructuredGraph graph = builder.build();
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope _ = debug.scope("Parsing", javaMethod, graph)) {
            graphBuilderSuite.apply(graph, getDefaultHighTierContext());
            Object[] args = getArgumentToBind();
            if (args != null) {
                bindArguments(graph, args);
            }
            return graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected static final Object NO_BIND = new Object();

    protected void bindArguments(StructuredGraph graph, Object[] argsToBind) {
        ResolvedJavaMethod m = graph.method();
        Object receiver = isStatic(m.getModifiers()) ? null : this;
        Object[] args = argsWithReceiver(receiver, argsToBind);
        JavaType[] parameterTypes = m.toParameterTypes();
        assert parameterTypes.length == args.length;
        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            Object arg = args[param.index()];
            if (arg != NO_BIND) {
                JavaConstant c = getSnippetReflection().forBoxed(parameterTypes[param.index()].getJavaKind(), arg);
                ConstantNode replacement = ConstantNode.forConstant(c, getMetaAccess(), graph);
                param.replaceAtUsages(replacement);
            }
        }
    }

    /**
     * Gets {@link ConstantNode}s to bind to {@link ParameterNode}s when
     * {@linkplain #parse(Builder, PhaseSuite) parsing} a graph. That is, parameter at index
     * {@code i} is replaced with element {@code i} in the returned array unless the element is
     * {@link #NO_BIND}.
     */
    protected Object[] getArgumentToBind() {
        return null;
    }

    protected PhaseSuite<HighTierContext> getEagerGraphBuilderSuite() {
        return getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withEagerResolving(true).withUnresolvedIsError(true));
    }

    /**
     * Gets the cancellable that should be associated with a graph being created by any of the
     * {@code parse...()} methods.
     *
     * @param method the method being parsed into a graph
     */
    protected Cancellable getCancellable(ResolvedJavaMethod method) {
        return null;
    }

    protected Plugins getDefaultGraphBuilderPlugins() {
        PhaseSuite<HighTierContext> suite = backend.getSuites().getDefaultGraphBuilderSuite();
        Plugins defaultPlugins = ((GraphBuilderPhase) suite.findPhase(GraphBuilderPhase.class).previous()).getGraphBuilderConfig().getPlugins();
        // defensive copying
        return new Plugins(defaultPlugins);
    }

    protected PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        // defensive copying
        return backend.getSuites().getDefaultGraphBuilderSuite().copy();
    }

    protected GraphBuilderPhase getDefaultGraphBuilderPhase() {
        return (GraphBuilderPhase) getDefaultGraphBuilderSuite().findPhase(GraphBuilderPhase.class).previous();
    }

    protected GraphBuilderPhase getDefaultGraphBuilderPhase(GraphBuilderConfiguration config) {
        return getDefaultGraphBuilderPhase().copyWithConfig(config);
    }

    /**
     * Registers extra invocation plugins for this test. The extra plugins are removed in the
     * {@link #afterTest()} method.
     * <p>
     * Subclasses overriding this method should always call the same method on the super class in
     * case it wants to register plugins.
     *
     * @param invocationPlugins
     */
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        invocationPlugins.register(GraalCompilerTest.class, new InvocationPlugin("breakpoint") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new BreakpointNode());
                return true;
            }
        });
        invocationPlugins.register(GraalCompilerTest.class, new InvocationPlugin("breakpoint", int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg0) {
                b.add(new BreakpointNode(arg0));
                return true;
            }
        });
        invocationPlugins.register(GraalCompilerTest.class, new InvocationPlugin("shouldBeOptimizedAway") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new NotOptimizedNode());
                return true;
            }
        });
        invocationPlugins.register(GraalCompilerTest.class, new InvocationPlugin("safepoint") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new SafepointNode());
                return true;
            }
        });
        invocationPlugins.register(GraalCompilerTest.class, new InvocationPlugin("delayConstantFoldingUntil", Object.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input, ValueNode stage) {
                assert stage.isConstant();
                GraphState.StageFlag stageFlag = GraphState.StageFlag.values()[stage.asJavaConstant().asInt()];
                b.addPush(JavaKind.Object, new DelayConstantFoldingNode(input, stageFlag));
                return true;
            }
        });
    }

    /**
     * Temporary node that canonicalizes to its value after given graph stage.
     */
    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    public static class DelayConstantFoldingNode extends FloatingNode implements Canonicalizable {
        public static final NodeClass<DelayConstantFoldingNode> TYPE = NodeClass.create(DelayConstantFoldingNode.class);

        private final GraphState.StageFlag stage;
        @Input ValueNode value;

        public DelayConstantFoldingNode(ValueNode value, GraphState.StageFlag stage) {
            super(TYPE, value.stamp(NodeView.DEFAULT));

            this.value = value;
            this.stage = stage;
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            if (graph() != null && graph().isAfterStage(stage)) {
                return value;
            }
            return this;
        }
    }

    /**
     * The {@link #testN(int, String, Object...)} method means multiple threads trying to initialize
     * this field.
     */
    private volatile InvocationPlugins invocationPluginExtensions;

    private InvocationPlugins extendedInvocationPlugins;

    protected PhaseSuite<HighTierContext> getCustomGraphBuilderSuite(GraphBuilderConfiguration gbConf) {
        PhaseSuite<HighTierContext> suite = getDefaultGraphBuilderSuite();
        ListIterator<BasePhase<? super HighTierContext>> iterator = suite.findPhase(GraphBuilderPhase.class);
        initializeInvocationPluginExtensions();
        GraphBuilderConfiguration gbConfCopy = editGraphBuilderConfiguration(gbConf.copy());
        iterator.remove();
        iterator.add(new HotSpotGraphBuilderPhase(gbConfCopy));
        return suite;
    }

    private void initializeInvocationPluginExtensions() {
        if (invocationPluginExtensions == null) {
            synchronized (this) {
                if (invocationPluginExtensions == null) {
                    InvocationPlugins invocationPlugins = new InvocationPlugins();
                    registerInvocationPlugins(invocationPlugins);
                    extendedInvocationPlugins = getReplacements().getGraphBuilderPlugins().getInvocationPlugins();
                    extendedInvocationPlugins.addTestPlugins(invocationPlugins, null);
                    invocationPluginExtensions = invocationPlugins;
                }
            }
        }
    }

    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        conf.getPlugins().prependInlineInvokePlugin(new InlineInvokePlugin() {

            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                BytecodeParserNeverInline neverInline = method.getAnnotation(BytecodeParserNeverInline.class);
                if (neverInline != null) {
                    return neverInline.invokeWithException() ? DO_NOT_INLINE_WITH_EXCEPTION : DO_NOT_INLINE_NO_EXCEPTION;
                }
                if (method.getAnnotation(BytecodeParserForceInline.class) != null) {
                    return InlineInfo.createStandardInlineInfo(method);
                }
                return bytecodeParserShouldInlineInvoke(b, method, args);
            }
        });
        return conf;
    }

    /**
     * Supplements {@link BytecodeParserForceInline} and {@link BytecodeParserNeverInline} in terms
     * of allowing a test to influence the inlining decision made during bytecode parsing.
     *
     * @see InlineInvokePlugin#shouldInlineInvoke(GraphBuilderContext, ResolvedJavaMethod,
     *      ValueNode[])
     */
    @SuppressWarnings("unused")
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return null;
    }

    @NodeInfo
    public static class NotOptimizedNode extends FixedWithNextNode {
        private static final NodeClass<NotOptimizedNode> TYPE = NodeClass.create(NotOptimizedNode.class);

        protected NotOptimizedNode() {
            super(TYPE, StampFactory.forVoid());
        }

    }

    protected Replacements getReplacements() {
        return getProviders().getReplacements();
    }

    protected Architecture getArchitecture() {
        return backend.getTarget().arch;
    }

    /**
     * Test if the current test runs on the given platform. The name must match the name given in
     * the {@link Architecture#getName()}.
     *
     * @param name The name to test
     * @return true if we run on the architecture given by name
     */
    protected boolean isArchitecture(String name) {
        return name.equals(getArchitecture().getName());
    }

    protected CanonicalizerPhase createCanonicalizerPhase() {
        return CanonicalizerPhase.create();
    }

    /**
     * Defines property name for seed value.
     */
    public static final String SEED_PROPERTY_NAME = "test.graal.random.seed";

    /**
     * Globally shared, lazily initialized random seed.
     */
    private static volatile Long randomSeed;

    /**
     * Returns a {@link java.util.Random} generator with a global seed specified by
     * {@link #SEED_PROPERTY_NAME} if it exists.
     * <p>
     * The used seed printed to stdout for reproducing test failures.
     */
    public static Random getRandomInstance() {
        if (randomSeed == null) {
            synchronized (GraalCompilerTest.class) {
                if (randomSeed == null) {
                    var seedLong = Long.getLong(SEED_PROPERTY_NAME);
                    var seed = seedLong != null ? seedLong : new Random().nextLong();
                    System.out.printf("Random generator seed: %d%n", seed);
                    System.out.printf("To re-run test with same seed, set \"-D%s=%d\" on command line.%n", SEED_PROPERTY_NAME, seed);
                    randomSeed = seed;
                }
            }
        }
        return new Random(randomSeed);
    }

    /** Counts all unit test graphs that we try to dump. */
    private static int dumpCount = 0;

    /** Maps the full text of each graph to a unique name for that graph. */
    private static HashMap<String, String> graphsAlreadyDumped = new HashMap<>();

    /** Maps each test name to the number of different graphs already generated from that test. */
    private static HashMap<String, Integer> graphNameCount = new HashMap<>();

    /** Maps a checker function to the number of different checker functions already generated with that base name. */
    private static HashMap<String, Integer> checkerNameCount = new HashMap<>();

    /** Maps a graph name to the number of different exception setup definitions with that base name. */
    private static HashMap<String, Integer> exceptionSetupNameCount = new HashMap<>();

    private static HashSet<String> graphsAlreadyNotified = new HashSet<>();

    /**
     * Dumps test cases (graph, inputs, output) into Isabelle format.
     *
     * Handles all methods except those which:
     *      - have any non-null arguments whose types are classes declared outside the test class.
     *      - have any null arguments whose types are classes declared outside the test class, and the test invokes
     *          a method.
     *      - have any object parameters which have non-primitive fields.
     *      - throw exceptions in a manner which isn't handled by {@link #exceptionKindSupported}.
     *
     * @param name the name of the graph for the test method being run.
     * @param method the test method being run.
     * @param fields a manager for the fields of the test class and any classes it declares.
     * @param result the result of the test {@code method}.
     * @param args the arguments passed to the test {@code method}.
     */
    public void dumpTest(String name, ResolvedJavaMethod method, GraalCompilerTest.Result result, VeriOptFields fields, Object... args) {
        final String cannotDump;
        if (hasExternalParameters(getNonPrimitiveParameters(args).keySet())) {
            cannotDump = "Not dumping test as it contains non-null parameters whose classes are defined outside of the test: " + name;
        } else {
            cannotDump = null;
        }
        if (cannotDump != null) {
            if (VeriOpt.DEBUG && !graphsAlreadyNotified.contains(name)) {
                graphsAlreadyNotified.add(name);
                System.err.println(cannotDump);
            }
            return;
        }
        dumpCount++;

        try {
            VeriOptTestUtil veriOpt = null;
            StructuredGraph graph = null;
            List<StructuredGraph> program = null;
            try {
                veriOpt = new VeriOptTestUtil();
                graph = veriOptGetGraph(method);

                if ((graph.getMethods().size() > 1) && hasExternalParameters(new HashSet<>(getNonPrimitiveParameterClasses(method, args)))) {
                    // Methods are invoked in the test
                    throw new RuntimeException("it contains null parameters whose classes are defined outside of the test, and invokes a method,");
                }

                // Get all graphs referenced recursively by this graph
                program = veriOptGraphCache.getReferencedGraphs(method);

                if (result.exception != null && (!exceptionKindSupported(graph, result.exception) || program.size() != 0)) {
                    // The test throws an exception whose format isn't handled yet in Isabelle
                    throw new RuntimeException("it contains an exception kind which isn't handled yet");
                }
            } catch (RuntimeException ex) {
                if (VeriOpt.DEBUG) {
                    System.err.println("Not dumping test as " + ex.getMessage() + " name: " + name);
                }
                return;
            }

            // Remove our graph from the list and replace it with this exact graph
            program.removeIf(duplicateGraph -> duplicateGraph.method().equals(method));
            program.add(0, graph);

            /* Instantiating fields */
            // Prepare to store fields
            fields.filterFields(program.toArray(new StructuredGraph[0]));
            fields.clearDynamic();

            // Get method which initializes instantiated static fields
            ResolvedJavaMethod clinit = method.getDeclaringClass().getClassInitializer();

            // Get the class & class names of the non-primitive parameters
            HashMap<String, Class<?>> nonPrimitiveParameters = getNonPrimitiveParameters(args);

            if (!fields.isEmpty() | !method.isStatic() | !primitiveArgs(args)) {
                // Create a graph to instantiate fields and/or non-primitive parameters (including self).
                program.add(fields.toGraph(getInitialOptions(), getDebugContext(), getMetaAccess(), fields.getContent(),
                       clinit, method, nonPrimitiveParameters, args, getClass()));
            }

            // Instantiate (to default values) fields in the test class (and any of its declared classes)
            fields.instantiateDynamicFields(program, getMetaAccess(), getClasses(getClassDeclarationList()));

            try {
                String argsStr = " " + veriOpt.valueList(generateArgumentsList(args, method),
                        getNonPrimitiveParameterIndexes(args, method));
                String graphToWrite;
                String valueToWrite;

                if (result.exception != null) {
                    /* The test throws an exception */
                    String resultStr = VeriOptValueEncoder.exception(result.exception, graph);
                    graphToWrite = "\n(* " + method.getDeclaringClass().getName() + "." + name + "*)\n"
                            + veriOpt.dumpProgram(program.toArray(new StructuredGraph[0]));
                    String mappingName = "JVMClasses " + (classesEncoded ? "{name}_mapping" : "[]");
                    String setupName = "prog0_{name}" + uniqueSuffix(graphToWrite, exceptionSetupNameCount);
                    String initialState =
                            "definition " + setupName + " :: \"(IRGraph \\<times> ID \\<times> MapState \\<times> Value list)\" where \n" +
                            "  \"" + setupName + " = (the ({name} ''" + VeriOpt.formatMethod(method) + "''), 0, new_map_state, " + argsStr + ")\"";
                    valueToWrite = initialState + "\n\n" +
                            "value \"exception_test ({name}, " + mappingName + ") " +
                            "([" + setupName + "," + setupName + "], new_heap) " + resultStr + "\"\n";
                } else {
                    /* The test has a value result */
                    if (result.returnValue != null && !primitiveArg(result.returnValue)) {
                        // Run object_test as we need to check the returned object
                        graphToWrite = "\n(* " + method.getDeclaringClass().getName() + "." + name + "*)\n"
                                + veriOpt.dumpProgram(program.toArray(new StructuredGraph[0]));
                        String mappingName = "JVMClasses " + (classesEncoded ? "{name}_mapping" : "[]");
                        String checkName = "check_" + name + "_" + (graphToWrite.hashCode() & 0xFF);
                        checkName = checkName + uniqueSuffix(checkName, checkerNameCount);
                        valueToWrite = veriOpt.checkResult(result.returnValue, checkName)
                                + String.format("value \"object_test ({name}, %s) ''%s''%s %s\"\n", mappingName,
                                veriOpt.getGraphName(graph), argsStr, checkName);
                    } else if (program.size() == 1) {
                        // Run static_test as there is no other graphs that
                        // need executing
                        String resultStr = (method.getSignature().getReturnKind().equals(JavaKind.Void)) ?
                                "(VOID_RETURN)" :
                                VeriOptValueEncoder.value(result.returnValue, true, false);
                        graphToWrite = "\n(* " + method.getDeclaringClass().getName() + "." + name + "*)\n"
                                + veriOpt.dumpGraph(graph);
                        valueToWrite = "value \"static_test {name} " + argsStr + " " + resultStr + "\"\n";
                    } else {
                        // Run program_test as there is other graphs that
                        // need to be executed
                        String resultStr = (method.getSignature().getReturnKind().equals(JavaKind.Void)) ?
                                "(VOID_RETURN)" :
                                VeriOptValueEncoder.value(result.returnValue, true, false);
                        graphToWrite = "\n(* " + method.getDeclaringClass().getName() + "." + name + "*)\n"
                                + veriOpt.dumpProgram(program.toArray(new StructuredGraph[0]));
                        String mappingName = "JVMClasses " + (classesEncoded ? "{name}_mapping" : "[]");
                        valueToWrite = "value \"program_test ({name}, " + mappingName + ") ''" + veriOpt.getGraphName(graph) + "''"
                                + argsStr + " " + resultStr + "\"\n";
                    }
                }
                // now write this test to an output file.
                String gName = graphsAlreadyDumped.get(graphToWrite);
                if (gName != null) {
                    // Graph has already been dumped, so we append to that existing file
                    try (PrintWriter out = new PrintWriter(new FileOutputStream(gName + ".test", true))) {
                        out.println(valueToWrite.replace("{name}", gName));
                    } catch (IOException ex) {
                        System.err.println("Error appending " + gName + " (" + dumpCount + "): " + ex);
                    }
                } else {
                    // Graph hasn't been dumped yet, so we choose a unique name/filename for it.
                    gName = "unit_" + name + uniqueSuffix(name, graphNameCount);
                    graphsAlreadyDumped.put(graphToWrite, gName);
                    try (PrintWriter out = new PrintWriter(gName + ".test")) {
                        out.println(graphToWrite.replace("{name}", gName));
                        out.println(valueToWrite.replace("{name}", gName));
                    } catch (IOException ex) {
                        System.err.println("Error writing " + gName + ": " + ex);
                    }
                }
            } catch (IllegalArgumentException ex) {
                if (VeriOpt.DEBUG) {
                    System.err.println("Not dumping test as " + ex.getMessage() + " name: " + name + " " + dumpCount);
                }
            }
        } catch (AssumptionViolatedException e) {
            // Suppress so that subsequent calls to this method within the
            // same Junit @Test annotated method can proceed.
        }
    }

    /**
     * Returns whether the exception kind in the graph is currently supported. Currently, only exceptions which
     * generate a BytecodeExceptionNode are supported.
     *
     * @param graph the graph generated for the test method.
     * @return {@code true} if the graph contains a supported exception kind, else {@code false}.
     * */
    private boolean exceptionKindSupported(StructuredGraph graph, Object result) {
        for (Node node : graph.getNodes()) {
            if (node instanceof BytecodeExceptionNode) {
                // Check that the expected result matches the BytecodeExceptionNode generated.
                Stamp exceptionStamp = ((ValueNode) node).stamp(NodeView.DEFAULT);
                if (exceptionStamp instanceof ObjectStamp) {
                    ObjectStamp objectStamp = (ObjectStamp) exceptionStamp;
                    String type = objectStamp.type() == null ? null : objectStamp.type().toClassName();
                    return type != null && type.equals(result.getClass().getName());
                } else {
                    // An exception's stamp type should always be an object
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Returns the class names of the non-primitive formal parameters of the test method.
     *
     * @param method the test method.
     * @param args the arguments passed to the {@code method}.
     * @return the class names of the {@code methods} non-primitive formal parameters.
     * */
    private List<String> getNonPrimitiveParameterClasses(ResolvedJavaMethod method, Object[] args) {
        List<String> classNames = new ArrayList<>();

        for (int i = 0; i < method.toParameterTypes().length; i++) {
            if (args.length == i) {
                // No parameters to return at this index
                break;
            }

            if (primitiveArg(args[i])) {
                // We only want to store non-primitive parameters
                continue;
            }

            JavaType current = method.toParameterTypes()[i];
            ResolvedJavaType resolvedJavaType = current.resolve(method.getDeclaringClass());
            classNames.add(resolvedJavaType.toClassName());
        }

        return classNames;
    }

    /**
     * Returns whether the class names given in {@code nonPrimitiveParameters} belong to classes declared outside the
     * test class (e.g., Object, String).
     *
     * @param nonPrimitiveParameters the class names being checked.
     * @return {@code true} if any of the classes in {@code nonPrimitiveParameters} are of a type declared outside the
     *         test class, else {@code false}.
     * */
    private boolean hasExternalParameters(Set<String> nonPrimitiveParameters) {
        for (String name : nonPrimitiveParameters) {
            if (!(getClasses(getClassDeclarationList()).containsKey(name))) {
                // This class isn't declared by the test class
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a mapping from class names to classes, for each of the test method's non-primitive arguments which are
     * non-null.
     *
     * @param args the arguments (primitive & non-primitive) passed to the test method.
     * @return a mapping from each non-primitive, non-null arguments class name to its class.
     * */
    private HashMap<String, Class<?>> getNonPrimitiveParameters(Object[] args) {
        HashMap<String, Class<?>> nonPrimitives = new HashMap<>();

        for (Object arg : args) {
            if (!primitiveArg(arg) && arg != null) {
                nonPrimitives.put(arg.getClass().getName(), arg.getClass());
            }
        }

        return nonPrimitives;
    }

    /**
     * Returns the indexes of test method's non-primitive parameters in the arguments list passed in.
     *
     * @param args the list of arguments passed to the test method.
     * @param method the test method.
     * @return the indexes of the test methods non-primitive parameters.
     * */
    private List<Integer> getNonPrimitiveParameterIndexes(Object[] args, ResolvedJavaMethod method) {
        List<Integer> indexes = new ArrayList<>();

        if (!method.isStatic()) {
            // First parameter (self) is non-primitive
            indexes.add(0);
        }

        // Add the offset to account for the manual addition of "self" to the args list for dynamic tests.
        int offset = (method.isStatic()) ? 0 : 1;

        for (int i = 0; i < args.length; i++) {
            if (!primitiveArg(args[i])) {
                indexes.add(i + offset);
            }
        }

        return indexes;
    }

    /**
     * Generates and returns the list of arguments which are passed to the Isabelle unit test.
     *
     * If the test method is {@code dynamic}, an implicit "self" argument is prepended to the input list. Non-primitive
     * parameters are translated into their location in the Isabelle heap (e.g., "Some 0").
     *
     * @param args the original argument list for the test method.
     * @param method the test method.
     * @return the original {@code args}, with non-primitive arguments updated to their Isabelle representation.
     * */
    private Object[] generateArgumentsList(Object[] args, ResolvedJavaMethod method) {
        if (args.length == 0) {
            return args;
        }

        List<Object> arguments = new ArrayList<>();

        // Need to use this over parameterized constructor call, otherwise null arguments cause NullPointerExceptions
        arguments.addAll(Arrays.asList(args));

        // Non-static methods take an implicit 'self' argument at index 0
        if (!method.isStatic()) {
            arguments.add(0, "(Some 0)");
        }

        // Add the offset to account for the manual addition of "self" to the args list for dynamic tests.
        int offset = (method.isStatic()) ? 0 : 1;
        int heapIndex = offset;

        // Loop through args and transform non-primitive parameters into their heap reference
        for (int i = 0; i < args.length; i++) {
            if (!primitiveArg(args[i])) {
                if (args[i] == null) {
                    arguments.set(i + offset, "None");
                } else {
                    arguments.set(i + offset, "(Some " + heapIndex + ")");
                    heapIndex++;
                }
            }
        }

        return arguments.toArray();
    }

    /**
     * Returns a list of all the class declarations for the test class; i.e., the test class itself and any classes it
     * declares.
     *
     * // TODO update this to use recursivelyGetDeclaredClasses to get all declared classes of arbitrary depth
     *
     * @return a list containing the test class and all of it's declared classes.
     * */
    private List<Class<?>> getClassDeclarationList() {
        List<Class<?>> classDeclarations = new ArrayList<>(List.of(getClass().getDeclaredClasses()));
        classDeclarations.add(0, getClass());
        return classDeclarations;
    }

    /**
     * Returns a mapping from a class' fully-qualified name, to the Class object for that class, for the
     * {@code classes} given.
     *
     * @return a mapping from class names to a Class object for that class, for the classes given.
     * */
    private HashMap<String, Class<?>> getClasses(List<Class<?>> classes) {
        // Populate the mapping
        HashMap<String, Class<?>> classMapping = new HashMap<>();
        for (Class<?> clazz : classes) {
            classMapping.put(clazz.getName(), clazz);
        }

        return classMapping;
    }

    /**
     * Recursively retrieves all the classes declared by a class, and any of it's declared classes.
     *
     * @param clazz the class whose declared classes are being retrieved
     * @param toSearch a list of classes whose declared classes still need to be retrieved
     * @return a list of all the classes declared by a class and any of it's declared classes
     * */
    public static List<Class<?>> recursivelyGetDeclaredClasses(Class<?> clazz, Queue<Class<?>> toSearch) {
        if (clazz == null) {
            return new ArrayList<>();
        }

        List<Class<?>> declaredClasses = List.of(clazz.getDeclaredClasses());

        if (declaredClasses.size() == 0) {
            // Base case: the class declares no classes
            return recursivelyGetDeclaredClasses(toSearch.poll(), toSearch);
        } else {
            // Recursive case: the class declares classes
            toSearch.addAll(declaredClasses);
            return Stream.concat(declaredClasses.stream(),
                    recursivelyGetDeclaredClasses(toSearch.poll(), toSearch).stream())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Generates and returns a unique suffix (integer value 1...m) corresponding to the usage count of the given
     * {@code name}, to avoid naming clashes when using the same base name in a given context.
     *
     * @param name the base name being appended.
     * @param usedCount a mapping from base names to their current amount of usages.
     * @return a suffix corresponding to the number of usages of the {@code name}, in the format "__n"
     * */
    private String uniqueSuffix(String name, Map<String, Integer> usedCount) {
        if (usedCount.containsKey(name)) {
            // that name is not unique, so add a unique suffix.
            int next = usedCount.get(name) + 1;
            usedCount.put(name, next);
            return String.format("__%d", next);
        } else {
            usedCount.put(name, 1);
            return "";  // no suffix needed
        }
    }

    /** True if all args are primitive. */
    private static boolean primitiveArgs(Object... args) {
        for (Object arg : args) {
            if (!primitiveArg(arg)) {
                return false;
            }
        }
        return true;
    }

    /** True if the arg is primitive. */
    private static boolean primitiveArg(Object arg) {
        return arg instanceof Integer ||
                        arg instanceof Long ||
                        arg instanceof Short ||
                        arg instanceof Character ||
                        arg instanceof Byte ||
                        arg instanceof Boolean ||
                        // arg instanceof String ||   // not supported yet
                        // Only accept floats and doubles if enabled
                        (VeriOpt.ENCODE_FLOAT_STAMPS && arg instanceof Float) ||
                        (VeriOpt.ENCODE_FLOAT_STAMPS && arg instanceof Double);
    }

    /** Adapted from getCode(). */
    private StructuredGraph veriOptGetGraph(ResolvedJavaMethod installedCodeOwner) {
        final CompilationIdentifier id = getOrCreateCompilationId(installedCodeOwner, null);
        StructuredGraph graphToCompile = parseForCompile(installedCodeOwner, id, getInitialOptions());
        // DebugContext debug = graphToCompile.getDebug();
        if (VeriOpt.DEBUG) {
            StringBuilder sb = new StringBuilder();
            sb.append("DEBUG: method ");
            sb.append(VeriOpt.formatMethod(installedCodeOwner));
            sb.append(" size=" + graphToCompile.getBytecodeSize());
            sb.append(" gives graph " + graphToCompile.toString());
            for (Node n : graphToCompile.getNodes()) {
                sb.append(";");
                sb.append(n.toString());
            }
            System.out.println(sb.toString());
        }
        return graphToCompile;
    }
}
