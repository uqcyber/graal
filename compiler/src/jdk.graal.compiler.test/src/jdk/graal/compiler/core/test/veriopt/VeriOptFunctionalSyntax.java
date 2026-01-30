package jdk.graal.compiler.core.test.veriopt;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.core.veriopt.VeriOptGraphTranslator;
import jdk.graal.compiler.core.veriopt.VeriOptIsabelleUtil;
import jdk.graal.compiler.core.veriopt.VeriOptNodeBuilder;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchorNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class VeriOptFunctionalSyntax {

    /**
     * Defines key reasons that translation could not occur for this program.
     * */
    private static final HashMap<String, String> exceptions = new HashMap<>();
    static {
        exceptions.put("CANNOT_TRANSLATE",   "the program contains a structure whose translation rules are unknown");
        exceptions.put("INTERMEDIATE_STEPS", "the program contains too many (%s) intermediate steps in block %s");
        exceptions.put("PHI_INPUTS",         "the program contains a phi which cannot be translated (input count: %s)");
        exceptions.put("UNWIND",             "the program contains an UnwindNode with an invalid exception");
        exceptions.put("BLOCK_SUCCESSORS",   "the program contains a block which has more than one or no successors");
        exceptions.put("VOID_RETURN",        "the program has a void return");
        exceptions.put("UNDEFINED_NODE",     "the graph contains a node (%s) which isnt in -Duq.irnodes=file");
        exceptions.put("UNHANDLED_LET",      "the graph contains a node (%s) with unhandled LetExpr or LetNode AbstractControls");
        exceptions.put("INVOKING",           "the AbstractProgram cannot invoke another method");
        exceptions.put("CANNOT_ENCODE",      "the AbstractProgram could not be encoded");
    }

    /**
     * Attempts to generate and return an Isabelle {@code AbstractProgram} based on the given {@code graph}. If there
     * was an issue generating the {@code AbstractProgram}, an empty {@code String} is returned.
     *
     * @param graph the graph to generate the {@code AbstractProgram} for.
     * @return an Isabelle {@code AbstractProgram} based on the given {@code graph}, or an empty {@code String}.
     * */
    public static String generateAbstractProgram(StructuredGraph graph) {
        try {
            return (new AbstractProgram(generateControlFlowGraph(graph)).generateProgram()).toString();
        } catch (RuntimeException exception) {
            System.err.println("Not generating AbstractProgram as " + exception.getMessage());
            return "";
        }
    }

    /**
     * Generates the {@link ControlFlowGraph} for the given {@code graph}. Implementation reworked from
     * {@link jdk.graal.compiler.nodes.loop.LoopsData#LoopsData(StructuredGraph, ControlFlowGraph)}.
     *
     * @param graph the graph whose {@code ControlFlowGraph} is being generated.
     * @return the {@code ControlFlowGraph} for the given {@code graph}.
     * @throws RuntimeException if there was an issue while constructing the {@code ControlFlowGraph}.
     * */
    private static ControlFlowGraph generateControlFlowGraph(StructuredGraph graph) {
        try {
            return ControlFlowGraph.newBuilder(graph)
                    .connectBlocks(true)
                    .modifiableBlocks(graph.isAfterStage(GraphState.StageFlag.FINAL_SCHEDULE))
                    .computeLoops(true)
                    .computeDominators(true)
                    .computePostdominators(true)
                    .computeFrequency(true).build();
        } catch (Throwable thrown) {
            throw new RuntimeException("a problem occurred while building the CFG: " + thrown.getMessage());
        }
    }

    /**
     * Represents a generic Isabelle {@code AbstractControl} structure
     * */
    private static class AbstractControl {

        // Keep track of the indexes for each ParameterExpr (phi) in the AbstractControl
        private final HashMap<PhiNode, Integer> phiIndexes;

        // The block which this AbstractControl represents
        private final HIRBlock block;

        // This block's path from start to finish
        private final ArrayList<FixedNode> blockPath;

        // This function block's name in the AbstractProgram
        private final String name;

        // The AbstractControl which encases this one
        private final AbstractControl encasingControl;

        // The AbstractProgram that this AbstractControl is in
        private final AbstractProgram program;

        /**
         * A constructor for building an {@code AbstractControl} within the given {@code program} based on the given
         * {@code block}. <br>
         *
         * {@code AbstractControl}s based on a {@code block} are the outermost {@code AbstractControl} (i.e., their
         * {@link #encasingControl} is {@code null}), and may or may not later contain inner {@code AbstractControl}s.
         * */
        public AbstractControl(HIRBlock block, AbstractProgram program) {
            this.encasingControl = null;
            this.program = program;
            this.block = block;
            this.name = block.toString().replace("B", "f");
            this.blockPath = getBlockPath();
            this.phiIndexes = setIndexesForPhis();
        }

        /**
         * A constructor for building an {@code AbstractControl} based on a pre-existing ({@code original})
         * {@code AbstractControl}. <br>
         *
         * If {@code creatingInner}, then an inner {@code AbstractControl} is being created; the {@code original}
         * becomes the new {@code AbstractControl}'s {@link #encasingControl}. Otherwise, a copy of the {@code original}
         * is created.
         * */
        private AbstractControl(AbstractControl original, boolean creatingInner) {
            // The name, phi indexes and program are the same for both copies and inner AbstractControls
            this.phiIndexes = original.phiIndexes;
            this.name = original.name;
            this.program = original.program;

            // Set the remaining fields
            if (creatingInner) {
                this.encasingControl = original;
                this.block = null;
                this.blockPath = null;

                // LetControls immediately store a reference to their inner controls
                if (original instanceof AbstractLetControl outerLetControl) {
                    outerLetControl.inner = this;
                }
            } else {
                this.encasingControl = original.encasingControl;
                this.block = original.block;
                this.blockPath = original.blockPath;
            }
        }

        /**
         * Returns this {@code AbstractControl}'s corresponding {@code HIRBlock}.
         *
         * @return the {@code HIRBlock} that this {@code AbstractControl} represents a step within. If this is the
         *         outermost {@code AbstractControl}, this is simply {@link #block}. Otherwise, we step outwards until
         *         the outermost {@code AbstractControl} is found.
         * */
        public HIRBlock getBlock() {
            return (encasingControl == null) ? block : encasingControl.getBlock();
        }

        /**
         * Returns the {@link #phiIndexes} for this {@code AbstractControl}.
         *
         * @return this {@code AbstractControl}'s {@code phiIndexes}.
         * */
        public HashMap<PhiNode, Integer> getPhiIndexes() {
            return phiIndexes;
        }

        /**
         * Generates and returns a mapping from each {@link PhiNode} which is used by a node in this {@link #blockPath},
         * to an index ranging on {@code [0...n]}, representing the index on which the Phi's value is passed to this
         * {@code AbstractControl} as a parameter. <br>
         *
         * @return a mapping from each {@code PhiNode} used by a node in this {@link #block}, to an index on
         *         {@code [0...n]}.
         * */
        private HashMap<PhiNode, Integer> setIndexesForPhis() {
            // Create a map for storing the phis indexes
            HashMap<PhiNode, Integer> indexes = new HashMap<>();

            // Iterate over every node in the graph
            for (Node node : this.block.getCfg().graph.getNodes()) {
                if ((!(node instanceof PhiNode phi)) || phi.hasNoUsages()) {
                    // Node isn't a phi, or it is, and it's never used; no need to store index
                    continue;
                }

                /* Use depth-first search to traverse up the usage chain, looking for a member of our blockPath */

                // Keep track of the nodes we've already visited
                ArrayList<Node> visited = new ArrayList<>();

                LinkedList<Node> toTraverse = new LinkedList<>();
                toTraverse.addFirst(node);

                while (!toTraverse.isEmpty()) {
                    // Get the node being checked
                    Node current = toTraverse.removeLast();

                    if (current instanceof FixedNode && this.blockPath.contains(current)) {
                        // Found a user in our blockPath
                        indexes.put(phi, indexes.size());
                        break;
                    }

                    // User wasn't found, move upwards by getting all the nodes that use this node
                    for (Node usingNode : current.usages()) {
                        if (!visited.contains(usingNode)) {
                            // We only want to check nodes that haven't yet been visited
                            toTraverse.addFirst(usingNode);
                        }
                    }

                    // Mark this node as visited
                    visited.add(current);
                }
            }

            return indexes;
        }

        /**
         * Generates and returns a {@code List} of {@link FixedNode}s representing the start-to-end path of the
         * {@link #block}.
         *
         * @return a {@code List} of {@code FixedNode}s representing the path of this {@code AbstractControl}'s
         *         {@code block}.
         * @throws RuntimeException if the {@code block}'s path has more steps than can currently be handled.
         * */
        private ArrayList<FixedNode> getBlockPath() {
            // Extract the node path from the block
            ArrayList<FixedNode> path = new ArrayList<>();
            block.getNodes().iterator().forEachRemaining(path::add);

            if (path.size() > 47) {
                // TODO: For now, programs with more than 46 intermediate steps in their blocks will not be considered.
                throw new RuntimeException(String.format(exceptions.get("INTERMEDIATE_STEPS"), path.size() - 1, block));
            }

            // Return the path
            return path;
        }

        /**
         * Helper method for {@link #getSetupLetControls(AbstractControl, boolean, List, List)}. <br>
         *
         * Generates {@link AbstractLetControl}s based on the provided {@code letControlNodes}. If
         * {@code isLetExprControls}, then {@link AbstractLetExpr}s are created, otherwise {@link AbstractLetNode}s are
         * created. <br>
         *
         * The generated {@code AbstractLetControl}s are linked and stored in the provided {@code setupControls} in
         * their order of creation.
         *
         * @param setupControls the {@code AbstractLetControl}s which have been generated so far.
         * @param letControlNodes the nodes which the generated {@code AbstractLetControl}s are based on.
         * @param original the original {@code AbstractControl} that the first generated {@code AbstractLetControl} is
         *                 based on.
         * @param creatingInner whether the first generated {@code AbstractLetControl} becomes an inner to the
         *                      {@code original}.
         * @param isLetExprControls whether {@code AbstractLetExpr}s or {@code AbstractLetNode}s are being generated
         *                          ({@code true} or {@code false}, respectively).
         * */
        private static void
        generateSetupLetControls(ArrayList<AbstractLetControl> setupControls, List<ValueNode> letControlNodes,
                                 AbstractControl original, boolean creatingInner, boolean isLetExprControls) {
            if (original == null || letControlNodes == null || setupControls == null) {
                // There must be a non-null original control & relevant mappings; shouldn't happen
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // Iterate through the nodes to be added to their relevant Isabelle mapping
            for (ValueNode node : letControlNodes) {
                if (node == null) {
                    // This node is an option type, and is currently 'None' (not 'Some ID'); nothing to store
                    continue;
                }

                if (!isLetExprControls && original.program.isInHeap(node)) {
                    // TODO this condition allows extra (sometimes unnecessary) LetExprs to be generated
                    // This is a LetNode control and the node is already in the heap, don't duplicate
                    continue;
                }

                // Get this control's outer and whether it's an inner control
                AbstractControl outer = (setupControls.isEmpty()) ? original : setupControls.getLast();
                boolean isInner = !(setupControls.isEmpty()) || creatingInner;

                // Create & store the control
                AbstractLetControl letControl = isLetExprControls ? new AbstractLetExpr(outer, node, isInner) :
                                                                    new AbstractLetNode(outer, node, isInner);
                setupControls.add(letControl);
                original.program.addToMapping(node, isLetExprControls);
            }
        }

        /**
         * Generates a series of linked {@link AbstractLetControl}s ({@link AbstractLetExpr}s and
         * {@link AbstractLetNode}s) based on the nodes provided in {@code letExprNodes} and {@code letNodeNodes},
         * respectively. The first (outermost) {@code AbstractLetControl} to be generated is returned. <br>
         *
         * The {@code AbstractLetExpr}s are generated first and in the same order as the provided {@code letExprNodes}.
         * The {@code AbstractLetNode}s are then generated in the same manner from {@code letNodeNodes}. <br>
         *
         * The first {@code AbstractLetControl} to be generated is based on the provided {@code original} and is an
         * inner control if {@code creatingInner}. Each subsequently generated {@code AbstractLetControl} becomes an
         * {@link AbstractLetControl#inner} to the previously generated {@code AbstractLetControl}. <br>
         *
         * (See {@link #generateSetupLetControls(ArrayList, List, AbstractControl, boolean, boolean)}).
         *
         * @param original the original {@code AbstractControl} that the first generated {@code AbstractLetControl} is
         *                 based on.
         * @param creatingInner whether the first generated {@code AbstractLetControl} becomes an inner to the
         *                      {@code original}.
         * @param letExprNodes the nodes which the generated {@code AbstractLetExpr}s are based on.
         * @param letNodeNodes the nodes which the generated {@code AbstractLetNode}s are based on.
         * @return the first (outermost) {@code AbstractLetControl} which is generated.
         * @throws RuntimeException if the provided {@code letExprNodes} and {@code letNodeNodes} are both either empty
         *                          or {@code null} (i.e., no nodes are provided to produce the
         *                          {@code AbstractLetControl}s).
         * */
        private static AbstractControl
        getSetupLetControls(AbstractControl original, boolean creatingInner, List<ValueNode> letExprNodes,
                            List<ValueNode> letNodeNodes) {
            // Transform empty node lists into nulls
            letExprNodes = (letExprNodes == null || letExprNodes.isEmpty()) ? null : letExprNodes;
            letNodeNodes = (letNodeNodes == null || letNodeNodes.isEmpty()) ? null : letNodeNodes;

            // Keep track of the controls in this setup
            ArrayList<AbstractLetControl> setupControls = new ArrayList<>();

            // Store the LetExpr and LetNode controls based on the provided nodes
            if (letExprNodes != null) {
                generateSetupLetControls(setupControls, letExprNodes, original, creatingInner, true);
            }

            if (letNodeNodes != null) {
                generateSetupLetControls(setupControls, letNodeNodes, original, creatingInner, false);
            }

            // Return the outermost AbstractControl
            if (!setupControls.isEmpty()) {
                return setupControls.getFirst();
            }

            // Nodes were provided to create AbstractControls, but they were already in the relevant mappings
            if (letExprNodes != null || letNodeNodes != null) {
                return original;
            }

            // No nodes were provided for LetExpr or LetNode AbstractControls
            throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
        }

        /**
         * Generates and returns an {@link AbstractLetControl} representing the node at {@code blockPath[index]}.
         *
         * @param original the {@code AbstractControl} that the returned {@code AbstractLetControl} is based on.
         * @param creatingInner whether the generated {@code AbstractLetControl} is an inner control to the
         *                      {@code original}.
         * @param blockPath the start-to-end path of a block, such that the returned {@code AbstractControl} represents
         *                  {@code blockPath[index]}.
         * @param index a value, such that the returned {@code AbstractControl} represents {@code blockPath[index]}.
         * @return an {@code AbstractLetControl} representing the node at {@code blockPath[index]}.
         * @throws RuntimeException if the node at {@code blockPath[index]} does not have a defined translation rule.
         * */
        private static AbstractControl generateLetControl(AbstractControl original, boolean creatingInner,
                                                          ArrayList<FixedNode> blockPath, int index) {
            // Get the node at the step we're on
            FixedNode node = blockPath.get(index);

            // Prepare the outermost and innermost AbstractControl of this Let construct
            AbstractControl outermost = null;
            AbstractControl innermost = null;

            // Handle the edge case of a LoadFieldNode with no object reference to store in a Let
            boolean isStaticLoadField = (node instanceof LoadFieldNode loadFieldNode && loadFieldNode.object() == null);

            if (node instanceof BytecodeExceptionNode || node instanceof NewInstanceNode ||
                node instanceof ControlFlowAnchorNode || isStaticLoadField) {
                /* Handle nodes which generate AbstractLetNodes */
                innermost = new AbstractLetNode(original, node, creatingInner);
                outermost = innermost;
            } else {
                /* Handle nodes which generate AbstractLetExprs */

                // Generate the necessary AbstractLetControl setup to represent this node's AbstractControl
                if (node instanceof ArrayLengthNode arrayLengthNode) {
                    outermost = getSetupLetControls(original, creatingInner,
                            Arrays.asList(arrayLengthNode.array()), null);
                }

                if (node instanceof FixedGuardNode fixedGuardNode) {
                    outermost = getSetupLetControls(original, creatingInner,
                            Arrays.asList(fixedGuardNode.condition()), null);
                }

                if (node instanceof IntegerDivRemNode integerDivRemNode) {
                    outermost = getSetupLetControls(original, creatingInner,
                            Arrays.asList(integerDivRemNode.getX(), integerDivRemNode.getY()), null);
                }

                if (node instanceof LoadFieldNode loadFieldNode) {
                    outermost = getSetupLetControls(original, creatingInner,
                            Arrays.asList(loadFieldNode.object()), null);
                }

                if (node instanceof LoadIndexedNode loadIndexedNode) {
                    outermost = getSetupLetControls(original, creatingInner,
                            Arrays.asList(loadIndexedNode.index(), loadIndexedNode.array()), null);
                }

                if (node instanceof NewArrayNode newArrayNode) {
                    outermost = getSetupLetControls(original, creatingInner,
                            Arrays.asList(newArrayNode.length()),null);
                }

                if (node instanceof StoreFieldNode storeFieldNode) {
                    outermost = getSetupLetControls(original, creatingInner,
                            Arrays.asList(storeFieldNode.value(), storeFieldNode.object()), null);
                }

                if (node instanceof StoreIndexedNode storeIndexedNode) {
                    outermost = getSetupLetControls(original, creatingInner,
                            Arrays.asList(storeIndexedNode.index(), storeIndexedNode.array(), storeIndexedNode.value()),
                            null);
                }

                // Finalise this node's AbstractControl if an AbstractLetControl setup was generated
                if (outermost != null) {
                    AbstractControl inner = (outermost instanceof AbstractLetControl outerLet) ? outerLet.getInnermost() : outermost;
                    innermost = new AbstractLetNode(inner, node, true);
                }
            }

            // Finalise the AbstractControl block
            if (outermost != null && innermost != null) {
                // Mark the node as stored
                original.program.addToHeap(node);

                // Generate the control structure for the next step in the path
                generateControl(innermost, true, blockPath, ++index);

                // Return the outermost AbstractControl
                return outermost;
            }

            // Node type isn't handled yet
            throw new RuntimeException(String.format(exceptions.get("UNHANDLED_LET"), node));
        }

        /**
         * Generates and returns an {@code AbstractControl} for the given {@code program} based on the given
         * {@code block}.
         *
         * @param block the block for which an {@code AbstractControl} is being generated.
         * @param program the program that the generated {@code AbstractControl} is in.
         * @return the {@code AbstractControl} based on the given {@code block}.
         * */
        public static AbstractControl generateControl(HIRBlock block, AbstractProgram program) {
            // Create a base AbstractControl for the given block
            AbstractControl controlBlock = new AbstractControl(block, program);

            // Generate the AbstractControl for this block
            return generateControl(controlBlock, false, controlBlock.blockPath, 1);
        }

        /**
         * Generates and returns an {@code AbstractControl} representing the node at {@code blockPath[index]}. If
         * {@code creatingInner}, the {@code original} becomes the {@link AbstractControl#encasingControl} of the
         * returned {@code AbstractControl}.
         *
         * @param original the {@code AbstractControl} that the returned {@code AbstractControl} is based on.
         * @param creatingInner whether the returned {@code AbstractControl} is an inner control to the
         *                      {@code original}.
         * @param blockPath the start-to-end path of a block, such that the returned {@code AbstractControl} represents
         *                  {@code blockPath[index]}.
         * @param index a value, such that the returned {@code AbstractControl} represents {@code blockPath[index]}.
         * @return an {@code AbstractControl} representing the node at {@code blockPath[index]}.
         * @throws RuntimeException if the node at {@code blockPath[index]} is an {@code UnwindNode}, and there were
         *                          issues with translating its {@link UnwindNode#exception()}.
         * */
        private static AbstractControl generateControl(AbstractControl original, boolean creatingInner,
                                                       ArrayList<FixedNode> blockPath, int index) {
            // Get the node representing this step in the path
            FixedNode step = blockPath.get(index);

            // First, handle AbstractControls which never have trailing AbstractControls (Return, Call, If, Unwind)
            if (step instanceof ReturnNode returnNode) {
                // Block returns a value
                return new AbstractReturn(original, returnNode, creatingInner);
            }

            if (step instanceof LoopEndNode loopEndNode) {
                // Block calls a loop
                return new AbstractCall(original, loopEndNode, null, creatingInner);
            }

            if (step instanceof IfNode ifNode) {
                // Block is a loop header or a standard If block
                return new AbstractIf(original, ifNode, creatingInner);
            }

            if (step instanceof EndNode end) {
                // Block calls its successor
                return new AbstractCall(original, end, null, creatingInner);
            }

            if (step instanceof UnwindNode unwindNode) {
                // Block unwinds an exception
                ValueNode exception = unwindNode.exception();
                if (original.program.isInHeap(exception) ||
                     (exception.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp stamp && stamp.type() != null)) {
                    return new AbstractUnwind(original, unwindNode, creatingInner);
                }

                // We cannot translate an Unwind whose exception is null and not in the heap
                throw new RuntimeException(exceptions.get("UNWIND"));
            }

            // Handle AbstractControls which have trailing AbstractControls (LetNode, LetExpr)
            return generateLetControl(original, creatingInner, blockPath, index);
        }

        @Override
        public String toString() {
            // Prepare the format string and its expected arguments
            String format;
            ArrayList<String> arguments;

            try {
                // Generate the encoding definition for this AbstractControl
                format = program.encodeControlBlockFormat(this);
                arguments = program.encodeControlBlockArguments(this);
            } catch (RuntimeException runtimeException) {
                // This AbstractControl isn't handled in encodeControlBlockFormat; it is the base AbstractControl type
                format = "AbstractControl %s";
                arguments = new ArrayList<>(Arrays.asList(this.name));
            }

            return VeriOptIsabelleUtil.StringFormatting.formatPlaceholderString(format, arguments.toArray(new String[0]));
        }
    }

    /**
     * Represents an Isabelle {@code AbstractCall} structure
     * */
    private static class AbstractCall extends AbstractControl {

        // The AbstractControl that this Call invokes
        private AbstractControl invoking;

        // A mapping from parameter indexes to their node value being passed in the call
        private final HashMap<Integer, Node> parameters = new HashMap<>();

        // The final node of this Call block
        private final FixedNode endNode;

        /**
         * Base constructor for an {@code AbstractCall}.
         * */
        public AbstractCall(AbstractControl original, FixedNode endNode, AbstractControl invokes,
                            boolean creatingInner) {
            super(original, creatingInner);
            this.endNode = endNode;
            this.invoking = invokes;
        }

        /**
         * A constructor for building an inner {@code AbstractCall} within an {@code outer} {@code AbstractControl},
         * which {@code invokes} the provided {@code AbstractControl}.
         * */
        public AbstractCall(AbstractControl outer, AbstractControl invokes) {
            this(outer, null, invokes, true);
        }

        /**
         * Returns the nodes in {@link #parameters} as a {@code List}, where each node's index in the {@code List} is
         * determined by its corresponding index in the parameter mapping.
         *
         * @return the {@code parameters} as a {@code List}, in the order they are passed into the call.
         * */
        public ArrayList<Node> getParameterList() {
            ArrayList<Node> parameterList = new ArrayList<>();

            for (int i = 0; i < parameters.size(); i++) {
                parameterList.add(i, parameters.get(i));
            }

            return parameterList;
        }
    }

    /**
     * Represents an Isabelle {@code AbstractReturn} structure
     * */
    private static class AbstractReturn extends AbstractControl {

        // The ReturnNode this AbstractControl represents
        private final ReturnNode returnNode;

        /**
         * A base constructor for an {@code AbstractReturn}.
         * */
        public AbstractReturn(AbstractControl original, ReturnNode returnNode, boolean creatingInner) {
            super(original, creatingInner);

            if (returnNode.result() == null) {
                // We cannot translate programs which have a void return type
                throw new RuntimeException(exceptions.get("VOID_RETURN"));
            }

            this.returnNode = returnNode;
        }
    }

    /**
     * Represents an Isabelle {@code AbstractIf} structure
     * */
    private static class AbstractIf extends AbstractControl {

        // The condition for the If
        private LogicNode condition;

        // The AbstractCall representing the true branch
        private AbstractCall trueBranch;

        // The AbstractCall representing the false branch
        private AbstractCall falseBranch;

        // The IfNode this AbstractControl represents
        private final IfNode ifNode;

        /**
         * A base constructor for an {@code AbstractIf}.
         * */
        public AbstractIf(AbstractControl original, IfNode ifNode, boolean creatingInner) {
            super(original, creatingInner);
            this.ifNode = ifNode;
        }

        /**
         * Sets the {@link #trueBranch} to be an inner {@code AbstractCall}, {@code invoking} the given
         * {@code AbstractControl}.
         *
         * @param invoking the {@code AbstractControl} that this If's {@link #trueBranch} calls.
         * */
        private void setTrueBranch(AbstractControl invoking) {
            this.trueBranch = new AbstractCall(this, invoking);
        }

        /**
         * Sets the {@link #falseBranch} to be an inner {@code AbstractCall}, {@code invoking} the given
         * {@code AbstractControl}.
         *
         * @param invoking the {@code AbstractControl} that this If's {@link #falseBranch} calls.
         * */
        private void setFalseBranch(AbstractControl invoking) {
            this.falseBranch = new AbstractCall(this, invoking);
        }
    }

    /**
     * An abstract representation of Isabelle {@code AbstractLetControl}s: {@link AbstractLetExpr} and
     * {@link AbstractLetNode}
     * */
    private static abstract class AbstractLetControl extends AbstractControl {

        // This AbstractLetControl's nested AbstractControl
        private AbstractControl inner;

        /**
         * A base constructor for an {@code AbstractLetControl}.
         * */
        public AbstractLetControl(AbstractControl original, boolean creatingInner) {
            super(original, creatingInner);
        }

        /**
         * Returns this {@code AbstractLetControl}'s {@link #inner} {@code AbstractControl}.
         *
         * @return this {@code AbstractLetControl}'s {@link #inner}.
         * */
        public AbstractControl getInner() {
            return inner;
        }

        /**
         * Finds and returns the innermost {@code AbstractControl} for this {@code AbstractLetControl}.
         *
         * @return the innermost {@code AbstractControl} for this {@code AbstractLetControl}.
         * */
        public AbstractControl getInnermost() {
            // The inner control has an inner; search downwards for it
            if (inner instanceof AbstractLetControl letControl) {
                return letControl.getInnermost();
            }

            // The inner control doesn't have an inner
            return (inner == null) ? this : inner;
        }
    }

    /**
     * Represents an Isabelle {@code AbstractLetExpr} structure
     * */
    private static class AbstractLetExpr extends AbstractLetControl {

        // The value of the expression
        private final ValueNode value;

        /**
         * A base constructor for an {@code AbstractLetExpr}.
         * */
        public AbstractLetExpr(AbstractControl original, ValueNode value, boolean creatingInner) {
            super(original, creatingInner);
            this.value = value;
        }
    }

    /**
     * Represents an Isabelle {@code AbstractLetNode} structure
     * */
    private static class AbstractLetNode extends AbstractLetControl {

        // The node being stored
        private final Node node;

        /**
         * A base constructor for an {@code AbstractLetNode}.
         * */
        public AbstractLetNode(AbstractControl original, Node node, boolean creatingInner) {
            super(original, creatingInner);
            this.node = node;
        }
    }

    /**
     * Represents an Isabelle {@code AbstractUnwind} structure
     * */
    private static class AbstractUnwind extends AbstractControl {

        // The UnwindNode this AbstractUnwind represents
        private final UnwindNode unwind;

        /**
         * A base constructor for an {@code AbstractUnwind}.
         * */
        public AbstractUnwind(AbstractControl original, UnwindNode unwind, boolean creatingInner) {
            super(original, creatingInner);
            this.unwind = unwind;
        }
    }

    /**
     * Handles the processing of {@code AbstractControl}s
     * */
    private static final class AbstractControlProcessor {

        // A mapping from blocks to their corresponding AbstractControl
        private final HashMap<HIRBlock, AbstractControl> controlBlocks;

        public AbstractControlProcessor(HashMap<HIRBlock, AbstractControl> blocks) {
            controlBlocks = blocks;
        }

        /**
         * Processes the given {@code block} by storing information relevant to the Isabelle translation in its
         * corresponding {@code AbstractControl}.
         *
         * @param block the block being processed.
         * */
        private void processControlBlock(HIRBlock block) {
            // Process the AbstractControl for this block
            processAbstractControl(controlBlocks.get(block));
        }

        /**
         * Processes the given {@code controlBlock} based on its type.
         *
         * @param controlBlock the {@code AbstractControl} being processed.
         * */
        private void processAbstractControl(AbstractControl controlBlock) {
            // Process the AbstractControl based on its type
            if (controlBlock instanceof AbstractCall abstractCall) {
                processCall(abstractCall);
            } else if (controlBlock instanceof AbstractIf abstractIf) {
                processIf(abstractIf);
            } else if (controlBlock instanceof AbstractLetControl abstractLetControl) {
                processAbstractControl(abstractLetControl.inner);
            } else if (controlBlock instanceof AbstractReturn || controlBlock instanceof AbstractUnwind) {
                // We already have all the information we need for encoding; do nothing
                return;
            } else {
                // Shouldn't get here
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }
        }

        /**
         * Processes the given {@code controlBlock} as an If.
         *
         * @param controlBlock the {@code AbstractIf} being processed.
         * */
        private void processIf(AbstractIf controlBlock) {
            // Get the IfNode for this ControlBlock
            IfNode ifNode = controlBlock.ifNode;

            // Store the condition which determines whether the false or true branch is run
            controlBlock.condition = ifNode.condition();

            // Find the AbstractControl which will be called on the true & false branch
            AbstractBeginNode trueStart = ifNode.trueSuccessor();
            AbstractBeginNode falseStart = ifNode.falseSuccessor();

            // Set the controlBlock's true and false branches based on their StartNode's
            for (AbstractControl control : controlBlocks.values()) {
                if (control.blockPath == null) {
                    // This is an inner control, and hence does not have a defined blockPath; shouldn't happen
                    continue;
                }

                if (control.blockPath.getFirst() == trueStart) {
                    controlBlock.setTrueBranch(control);
                }

                if (control.blockPath.getFirst() == falseStart) {
                    controlBlock.setFalseBranch(control);
                }
            }

            if (controlBlock.trueBranch == null || controlBlock.falseBranch == null) {
                // True or false branch was not found in CFG blocks
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // We need to determine what is being passed to the trueBranch & falseBranch calls
            addIfBranchCallParameters(controlBlock, true);
            addIfBranchCallParameters(controlBlock, false);
        }

        /**
         * Populates the given {@code controlBlock}s {@link AbstractIf#trueBranch} or {@link AbstractIf#falseBranch}
         * {@link AbstractCall#parameters} with the parameters being passed into the Call. The branch whose
         * {@code parameters} are being updated is dependent on the given {@code branchType}.
         *
         * @param controlBlock the {@code AbstractIf} being processed.
         * @param branchType whether the branch whose {@code parameters} are being extended is the {@code trueBranch}
         *                   ({@code true}) or {@code falseBranch} ({@code false}).
         * @throws RuntimeException if the {@code controlBlock}'s relevant branch is {@code null}, or there were issues
         *                          translating the phis.
         * */
        private void addIfBranchCallParameters(AbstractIf controlBlock, boolean branchType) {
            // Get the expected branch
            AbstractCall branch = (branchType) ? controlBlock.trueBranch : controlBlock.falseBranch;

            if (branch == null) {
                // No branch of the given type has been set for this If
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            for (PhiNode phi : branch.invoking.phiIndexes.keySet()) {
                if (controlBlock.getPhiIndexes().get(phi) == null) {
                    // We cannot translate a branch's phi which is unknown to us
                    throw new RuntimeException(String.format(exceptions.get("PHI_INPUTS"), phi.valueCount()));
                }

                // We need to call the block with the phis at the index that they expect
                branch.parameters.put(branch.invoking.phiIndexes.get(phi), phi);
            }
        }

        /**
         * Helper method which determines how the given {@code controlBlock} should be processed based on the type of
         * its {@link AbstractCall#endNode}.
         *
         * @param controlBlock the {@code AbstractCall} being processed.
         * */
        private void processCall(AbstractCall controlBlock) {
            // Get the endNode for this block to determine how to proceed
            FixedNode endNode = controlBlock.endNode;

            if (endNode instanceof LoopEndNode loopEndNode) {
                processCall(controlBlock, loopEndNode);
            } else if (endNode instanceof EndNode end) {
                processCall(controlBlock, end);
            }
        }

        /**
         * Processes the given {@code controlBlock} as a Call, whose {@code endNode} is an {@link EndNode}. <br>
         *
         * {@code controlBlock}s of this structure may only have a single successor (for now). Thus, they simply call
         * that successor with any expected phi values.
         *
         * @param controlBlock the {@code AbstractCall} being processed.
         * @param endNode the final node of the block represented by the given {@code controlBlock}.
         * @throws RuntimeException if the given {@code controlBlock}'s {@code HIRBlock} does not have exactly one
         *                          successor.
         * */
        private void processCall(AbstractCall controlBlock, EndNode endNode) {
            // Extract the block
            HIRBlock block = controlBlock.getBlock();

            if (block.getSuccessorCount() != 1) {
                // This block has more than one or no successors; can't translate
                throw new RuntimeException(exceptions.get("BLOCK_SUCCESSORS"));
            }

            // Finalise the call
            finaliseCall(controlBlock, controlBlocks.get(block.getFirstSuccessor()));
        }

        /**
         * Processes the given {@code controlBlock} as a Call, whose {@code endNode} is a {@link LoopEndNode}. <br>
         *
         * {@code controlBlock}s of this structure simply call the loop header, passing in the changes made inside the
         * loop.
         *
         * @param controlBlock the {@code AbstractCall} being processed.
         * @param endNode the final node of the block represented by the given {@code controlBlock}.
         * */
        private void processCall(AbstractCall controlBlock, LoopEndNode endNode) {
            // Get the AbstractControl for the loop that this block calls
            AbstractControl loopBlock = null;

            // Iterate through each block looking for the loop being called
            for (AbstractControl control : controlBlocks.values()) {
                if (control.blockPath == null || control.block == null) {
                    // This is an inner control, and hence does not have a defined blockPath or block; shouldn't happen
                    continue;
                }

                if (control.block.isLoopHeader() && control.blockPath.getFirst() == endNode.loopBegin()) {
                    loopBlock = control;
                    break;
                }
            }

            // Finalise the call
            finaliseCall(controlBlock, loopBlock);
        }

        /**
         * Finalises the given {@code controlBlock} by storing the {@code AbstractControl} it's {@code invoking} and
         * extending the call {@code parameters}.
         *
         * @param controlBlock the {@code AbstractCall} being finalised.
         * @param invoking the {@code AbstractControl} being invoked by the given {@code controlBlock}.
         * @throws RuntimeException if the given {@code AbstractControl} being invoked is {@code null}.
         * */
        private void finaliseCall(AbstractCall controlBlock, AbstractControl invoking) {
            if (invoking == null) {
                // Cannot invoke a null AbstractControl; shouldn't happen
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // Store the block being called
            controlBlock.invoking = invoking;

            // Get the parameters being passed into the call
            addParameterPhis(controlBlock);
        }

        /**
         * Extend the call {@link AbstractCall#parameters} of the given {@code controlBlock} to include any phis
         * expected by the {@code AbstractControl} this {@code controlBlock} is {@link AbstractCall#invoking}.
         *
         * @param controlBlock the {@code AbstractCall} whose {@code parameters} are being extended.
         * @throws RuntimeException if the {@code AbstractControl} the {@code controlBlock} is {@code invoking} is an
         *                          inner {@code AbstractControl}, or there were issues translating the phis.
         * */
        private void addParameterPhis(AbstractCall controlBlock) {
            if (controlBlock.invoking.blockPath == null) {
                // The control being invoked is an inner control; shouldn't happen
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // Set the phiIndex based on the predecessor index for this block in the invoking block
            int phiIndex = -1;
            HIRBlock invokingBlock = controlBlock.invoking.getBlock();

            for (int i = 0; i < invokingBlock.getPredecessorCount(); i++) {
                if (controlBlock.getBlock() == invokingBlock.getPredecessorAt(i)) {
                    phiIndex = i;
                    break;
                }
            }

            // Iterate over every phi expected by the block being invoked
            for (PhiNode phi : controlBlock.invoking.phiIndexes.keySet()) {
                if (phiIndex == -1 || phi.merge() != controlBlock.invoking.blockPath.getFirst()) {
                    // Cannot translate if the phis merge node isn't the first in the path, or our phiIndex is invalid
                    throw new RuntimeException(String.format(exceptions.get("PHI_INPUTS"), phi.valueCount()));
                }

                // We need to call the block with the phis at the index that they expect
                controlBlock.parameters.put(controlBlock.invoking.phiIndexes.get(phi), phi.valueAt(phiIndex));
            }
        }
    }

    /**
     * Represents an Isabelle {@code AbstractProgram}
     * */
    private static final class AbstractProgram {

        /**
         * Defines nodes which are specifically used within inter-procedural programs.
         * */
        private static final ArrayList<Class<? extends Node>> interproceduralNodes = new ArrayList<>();
        static {
            interproceduralNodes.add(InvokeNode.class);
            interproceduralNodes.add(InvokeWithExceptionNode.class);
            interproceduralNodes.add(MethodCallTargetNode.class);
        }

        /**
         * Defines the Isabelle format of each {@code AbstractControl} structure.
         * <pre>
         *     AbstractReturn ->  (ReturnExpr (%s))
         *     AbstractCall ->    (Call %s %s)
         *     AbstractIf ->      (If (%s) %s %s)
         *     AbstractLetNode -> (LetNode %s %s %s)
         *     AbstractLetExpr -> (LetExpr %s (%s) %s)
         *     AbstractUnwind ->  (Unwind %s)
         * </pre>
         * */
        private static final HashMap<Class<? extends AbstractControl>, String> controlFormats = new HashMap<>();
        static {
            controlFormats.put(AbstractReturn.class,  "(ReturnExpr (%s))");
            controlFormats.put(AbstractCall.class,    "(Call %s %s)");
            controlFormats.put(AbstractIf.class,      "(If (%s)\n\t\t%s\n\t\t%s)");
            controlFormats.put(AbstractLetNode.class, "(LetNode %s %s\n\t\t%s)");
            controlFormats.put(AbstractLetExpr.class, "(LetExpr %s (%s)\n\t\t%s)");
            controlFormats.put(AbstractUnwind.class,  "(Unwind %s)");
        }

        // Nodes which are mapped in the Isabelle heap for this AbstractProgram
        private final ArrayList<Node> isabelleHeapNodes = new ArrayList<>();

        // Nodes which are mapped in the Isabelle IRExpr mapping for this AbstractProgram
        private final ArrayList<Node> isabelleIRExprNodes = new ArrayList<>();

        // A mapping from HIRBlocks to their corresponding AbstractControl
        private final HashMap<HIRBlock, AbstractControl> controlBlocks = new HashMap<>();

        // The Isabelle AbstractProgram encoding
        private final StringBuilder program = new StringBuilder();

        // The ControlFlowGraph for this AbstractProgram
        private final ControlFlowGraph controlFlowGraph;

        // The ControlFlowGraph's blocks in reverse post-order
        private final HIRBlock[] blocks;

        public AbstractProgram(ControlFlowGraph controlFlowGraph) {
            this.controlFlowGraph = controlFlowGraph;
            this.blocks = controlFlowGraph.reversePostOrder();
        }

        /**
         * Returns whether the given {@code node} is in this program's Isabelle heap.
         *
         * @param node the node being checked.
         * @return {@code true} if the given {@code node} is in the program's Isabelle heap, else {@code false}.
         * */
        public boolean isInHeap(Node node) {
            return isabelleHeapNodes.contains(node);
        }

        /**
         * Returns whether the given {@code node} is in this program's Isabelle IRExpr mapping.
         *
         * @param node the node being checked.
         * @return {@code true} if the given {@code node} is in the program's Isabelle IRExpr mapping, else
         *         {@code false}.
         * */
        public boolean isInIRExprMapping(Node node) {
            return isabelleIRExprNodes.contains(node);
        }

        /**
         * Adds the given {@code node} to this program's Isabelle heap ({@link #isabelleHeapNodes}), if it isn't
         * already there.
         *
         * @param node the node being added.
         * */
        public void addToHeap(Node node) {
            if (isInHeap(node)) {
                return;
            }

            isabelleHeapNodes.add(node);
        }

        /**
         * Adds the given {@code node} to this program's Isabelle IRExpr mapping ({@link #isabelleIRExprNodes}), if it
         * isn't already there.
         *
         * @param node the node being added.
         * */
        public void addToIRExprMapping(Node node) {
            if (isInIRExprMapping(node)) {
                return;
            }

            isabelleIRExprNodes.add(node);
        }

        /**
         * Adds the given {@code node} to this program's relevant mapping, if it isn't already there. If
         * {@code isIRExprMapping}, then the {@code node} is added to {@link #isabelleIRExprNodes}, otherwise it is
         * added to {@link #isabelleHeapNodes}.
         *
         * @param node the node being added.
         * @param isIRExprMapping whether the mapping that the {@code node} is being added to is
         *                         {@code isabelleIRExprNodes} ({@code true}) or {@code isabelleHeapNodes}
         *                         ({@code false}).
         * */
        public void addToMapping(Node node, boolean isIRExprMapping) {
            if (isIRExprMapping) {
                addToIRExprMapping(node);
            } else {
                addToHeap(node);
            }
        }

        /**
         * Generates the Isabelle {@code AbstractProgram} and stores it in {@link #program}, and returns this object.
         *
         * @return this {@code AbstractProgram} with its Isabelle {@code AbstractProgram} encoding stored in
         *         {@code program}.
         * */
        public AbstractProgram generateProgram() {
            if (!VeriOpt.ENCODE_FUNCTIONAL || !canBeEncoded()) {
                // An AbstractProgram is not being or cannot be generated for this ControlFlowGraph
                return this;
            }

            // Generate and process the CFGs AbstractControls, collecting information necessary for translation
            processAbstractControls();

            // Encode the AbstractControl structures
            encodeProgram();

            // Return this AbstractProgram with its completed encoding
            return this;
        }

        /**
         * Encodes the {@link #controlBlocks} into an Isabelle {@code AbstractProgram} representing the
         * {@link #controlFlowGraph}. The resultant encoding is stored inside {@link #program}.
         *
         * @throws RuntimeException if there was an issue encoding the program.
         * */
        private void encodeProgram() {
            // Find the program's initial AbstractControl block (f0) to start the continuation definition
            for (AbstractControl controlBlock : controlBlocks.values()) {
                if (Objects.equals(controlBlock.name, "f0")) {
                    // We need to increment the phi indexes to account for parameters passed into the program
                    incrementPhis();

                    // Encode the program
                    String programSyntax = "\n\ndefinition {name}_functional :: AbstractProgram where\n  \"{name}_functional =\n\t%s\n  \"\n";
                    program.append(programSyntax.replace("%s", encodeAsContinuation(controlBlock)));
                    return;
                }
            }

            // The initial AbstractControl could not be found
            throw new RuntimeException(exceptions.get("CANNOT_ENCODE"));
        }

        /**
         * Encodes the given {@code initialControl} as a continuation and returns the encoding.
         *
         * @param initialControl the {@code AbstractControl} being encoded as a continuation.
         * @return the given {@code initialControl} encoded as a continuation.
         * @throws RuntimeException if the given {@code initialControl} is {@code null}.
         * */
        private String encodeAsContinuation(AbstractControl initialControl) {
            if (initialControl == null) {
                // We cannot produce an encoding for a null AbstractControl
                throw new RuntimeException(exceptions.get("CANNOT_ENCODE"));
            }

            // Keep track of the visited and processing AbstractControls
            ArrayList<AbstractControl> letFDefined = new ArrayList<>();
            LinkedList<AbstractControl> toProcess = new LinkedList<>();

            // Prepare this AbstractControl for processing and encoding
            String encoding = initialControl.toString();
            toProcess.addFirst(initialControl);

            while (!toProcess.isEmpty()) {
                // Get the AbstractControl being processed
                AbstractControl processing = toProcess.removeLast();

                // AbstractLetControls may have an inner AbstractCall that must be processed
                while (processing instanceof AbstractLetControl abstractLetControl) {
                    processing = abstractLetControl.inner;
                }

                // AbstractIfs mark their branches for processing
                if (processing instanceof AbstractIf abstractIf) {
                    AbstractCall trueBranch = abstractIf.trueBranch;
                    AbstractCall falseBranch = abstractIf.falseBranch;

                    if (!letFDefined.contains(trueBranch)) {
                        toProcess.addFirst(trueBranch);
                    }

                    if (!letFDefined.contains(falseBranch)) {
                        toProcess.addFirst(falseBranch);
                    }
                }

                // AbstractCalls extend the continuation with the invoking definition
                if (processing instanceof AbstractCall abstractCall) {
                    AbstractControl invoking = abstractCall.invoking;

                    if (!letFDefined.contains(invoking)) {
                        toProcess.addFirst(invoking);
                        letFDefined.add(invoking);
                        encoding = String.format("(LetF %s \n\t\t%s \n\t%s)",
                                VeriOptIsabelleUtil.Syntax.toIsabelleString(invoking.name), invoking, encoding);
                    }
                }
            }

            // If there are no LetFs, indent the first line of the definition to align any AbstractLetControls
            return (encoding.contains("LetF") ? "" : "\t") + encoding;
        }

        /**
         * Increments the parameter indexes stored by all {@code PhiNode}s ({@link AbstractControl#phiIndexes}) which,
         * upon translation, become ParameterExprs to a function block. <br>
         *
         * This incrementation 'shifts' the function block parameters forwards to allow the program's parameters to
         * maintain their original parameter index, which simplifies translation and simulates program scope by allowing
         * any {@code AbstractControl} to access the program's parameters on a common index.
         * */
        private void incrementPhis() {
            // Get the amount of program parameters
            int parameters = this.controlFlowGraph.graph.getNodes(ParameterNode.TYPE).count();

            // Increment the Phi indexes
            for (AbstractControl control : controlBlocks.values()) {
                for (PhiNode phi : control.phiIndexes.keySet()) {
                    int index = control.phiIndexes.get(phi);
                    control.phiIndexes.put(phi, index + parameters);
                }
            }
        }

        /**
         * Generates and returns the encoding format for the given {@code controlBlock}.
         *
         * @param controlBlock the {@code AbstractControl} whose encoding format is being generated.
         * @return the encoding format for the given {@code controlBlock}.
         * @throws RuntimeException if there were issues encoding the {@code controlBlock}.
         * */
        private String encodeControlBlockFormat(AbstractControl controlBlock) {
            // Return, Call and Unwind controls just return their default format
            if (controlBlock instanceof AbstractReturn || controlBlock instanceof AbstractCall ||
                controlBlock instanceof AbstractUnwind) {
                return controlFormats.get(controlBlock.getClass());
            }

            // If controls must encode the format of their call branches
            if (controlBlock instanceof AbstractIf abstractIf) {
                String trueFormat = encodeControlBlockFormat(abstractIf.trueBranch);
                String falseFormat = encodeControlBlockFormat(abstractIf.falseBranch);

                // Add the true & false encodings to the end
                return String.format(controlFormats.get(abstractIf.getClass()), "%s", trueFormat, falseFormat);
            }

            // Let controls are extended by their inner control's encoding
            if (controlBlock instanceof AbstractLetControl abstractLetControl) {
                String innerFormat = encodeControlBlockFormat(abstractLetControl.inner);

                // Add the inner format encoding to the end
                return String.format(controlFormats.get(abstractLetControl.getClass()), "%s", "%s", innerFormat);
            }

            // Shouldn't happen
            throw new RuntimeException(exceptions.get("CANNOT_ENCODE"));
        }

        /**
         * Encodes and returns the arguments which will replace the placeholders in the given {@code controlBlock}'s
         * {@link #encodeControlBlockFormat(AbstractControl)}, to produce its Isabelle definition.
         *
         * @param controlBlock the {@code AbstractControl} whose arguments are being encoded.
         * @return the arguments to replace the placeholders in the given {@code controlBlock}'s
         *         {@link #encodeControlBlockFormat(AbstractControl)}, in their Isabelle syntax.
         * */
        private ArrayList<String> encodeControlBlockArguments(AbstractControl controlBlock) {
            ArrayList<String> arguments = new ArrayList<>();

            // Get the expected indexes of this controlBlock's phis
            HashMap<PhiNode, Integer> phis = controlBlock.getPhiIndexes();

            // Return controls simply encode the expression they're returning
            if (controlBlock instanceof AbstractReturn abstractReturn) {
                arguments.add(VeriOptIsabelleUtil.encodeIRExpr(abstractReturn.returnNode.result(), true, phis));
            }

            // Call controls must encode the name of the function they're calling, and the arguments being passed in
            if (controlBlock instanceof AbstractCall abstractCall) {
                // Add the name of the block being called
                arguments.add(VeriOptIsabelleUtil.Syntax.toIsabelleString(abstractCall.invoking.name));

                // Generate and add the parameters to the call
                ArrayList<String> encodedParameters = VeriOptIsabelleUtil.encodeIRExprs(abstractCall.getParameterList(),
                        true, phis);
                arguments.add(VeriOptIsabelleUtil.Syntax.toIsabelleArray(encodedParameters));
            }

            // If controls must encode their condition and branches
            if (controlBlock instanceof AbstractIf abstractIf) {
                arguments.add(VeriOptIsabelleUtil.encodeIRExpr(abstractIf.condition, true, phis));
                arguments.addAll(encodeControlBlockArguments(abstractIf.trueBranch));
                arguments.addAll(encodeControlBlockArguments(abstractIf.falseBranch));
            }

            // LetNode controls must encode their node's ID & the node itself, and any trailing controls
            if (controlBlock instanceof AbstractLetNode abstractLetNode) {
                arguments.add(VeriOptIsabelleUtil.asNodeID(abstractLetNode.node));
                arguments.add(new VeriOptNodeBuilder(abstractLetNode.node).build().asAbstractProgramNode());
                arguments.addAll(encodeControlBlockArguments(abstractLetNode.getInner()));
            }

            // LetExpr controls must encode their node's ID and its IRExpr, and any trailing controls
            if (controlBlock instanceof AbstractLetExpr abstractLetExpr) {
                arguments.add(VeriOptIsabelleUtil.asNodeID(abstractLetExpr.value));
                arguments.add(VeriOptIsabelleUtil.encodeIRExpr(abstractLetExpr.value, true, phis));
                arguments.addAll(encodeControlBlockArguments(abstractLetExpr.getInner()));
            }

            // Unwind controls must encode their node's ID
            if (controlBlock instanceof AbstractUnwind abstractUnwind) {
                arguments.add(VeriOptIsabelleUtil.asNodeID(abstractUnwind.unwind.exception()));
            }

            return arguments;
        }

        /**
         * Creates the {@code AbstractControl} structures corresponding to the {@link #controlFlowGraph}'s
         * {@code HIRBlock}s, and processes them to extract the information necessary for translation.
         * */
        private void processAbstractControls() {
            // Map each HIRBlock to its corresponding AbstractControl
            for (HIRBlock block : blocks) {
                controlBlocks.put(block, AbstractControl.generateControl(block, this));
            }

            // Create the AbstractControl processor
            AbstractControlProcessor processor = new AbstractControlProcessor(controlBlocks);

            // Process all the AbstractControls from end-to-start
            for (int i = blocks.length - 1; i >= 0; i--) {
                processor.processControlBlock(blocks[i]);
            }
        }

        /**
         * Returns whether this {@code AbstractProgram} can be encoded into an Isabelle {@code AbstractProgram}.
         *
         * @return {@code true} if this {@code AbstractProgram} can be encoded.
         * @throws RuntimeException if this {@code AbstractProgram} cannot be encoded, with an accompanying reason.
         * */
        private boolean canBeEncoded() {
            Node undefinedNode = getUndefinedNode();
            if (undefinedNode != null) {
                // At least one of the nodes in the graph does not have a corresponding Isabelle definition
                throw new RuntimeException(String.format(exceptions.get("UNDEFINED_NODE"), undefinedNode));
            }

            if (isInvoking()) {
                // Currently, AbstractPrograms cannot perform method invocation
                throw new RuntimeException(exceptions.get("INVOKING"));
            }

            // If we got here, no exception occurred
            return true;
        }

        /**
         * Returns whether this {@code AbstractProgram} invokes another method. The nodes which signify a method
         * invocation are defined in {@link #interproceduralNodes}.
         *
         * @return {@code true} if this {@code AbstractProgram} invokes another method, else {@code false}.
         * */
        private boolean isInvoking() {
            for (Node node : controlFlowGraph.graph.getNodes()) {
                if (interproceduralNodes.contains(node.getClass())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns a node in this {@code AbstractProgram}'s {@link #controlFlowGraph} which does not currently have an
         * Isabelle definition, if one exists. <br>
         *
         * Nodes which have an Isabelle definition will return {@code true} for
         * {@link VeriOptGraphTranslator#isInIrNodes(Node)}.
         *
         * @return a node which does not have a corresponding Isabelle definition, if one exists in the
         *         {@code controlFlowGraph}, else {@code null}.
         * */
        private Node getUndefinedNode() {
            for (Node node : controlFlowGraph.graph.getNodes()) {
                if (!VeriOptGraphTranslator.isInIrNodes(node)) {
                    return node;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return program.toString();
        }
    }
}
