package jdk.graal.compiler.core.test.veriopt;

import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.core.veriopt.VeriOptGraphTranslator;
import jdk.graal.compiler.core.veriopt.VeriOptIsabelleUtil;
import jdk.graal.compiler.core.veriopt.VeriOptNodeBuilder;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FixedBinaryNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
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
import java.util.HashMap;
import java.util.LinkedList;

public class VeriOptFunctionalSyntax {

    /**
     * Defines key reasons that translation could not occur for this program.
     * */
    private static final HashMap<String, String> exceptions = new HashMap<>();
    static {
        exceptions.put("CANNOT_TRANSLATE",   "the program contains a structure whose translation rules are unknown");
        exceptions.put("INTERMEDIATE_STEPS", "the program contains multiple (%s) steps in block %s");
        exceptions.put("PHI_INPUTS",         "the program contains a phi which cannot be translated (input count: %s)");
        exceptions.put("UNDEFINED_NODE",     "the graph contains a node (%s) which isnt in -Duq.irnodes=file");
        exceptions.put("UNHANDLED_LET",      "the graph contains a node (%s) with unhandled LetExpr or LetNode AbstractControls");
        exceptions.put("INVOKING",           "the AbstractProgram cannot invoke another method");
    }

    /**
     * Attempts to generate and return an Isabelle AbstractProgram based on the given {@code graph}. If there was an
     * issue generating the AbstractProgram, an empty string is returned.
     *
     * @param graph the graph to generate the AbstractProgram for.
     * @return an Isabelle AbstractProgram based on the given {@code graph}, or an empty string.
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
     * @param graph the graph whose {@link ControlFlowGraph} is being generated.
     * @return the {@link ControlFlowGraph} for the given graph.
     * @throws RuntimeException if there was an issue while constructing the {@link ControlFlowGraph}.
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
     * Represents a generic Isabelle AbstractControl structure
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

        /**
         * A constructor for building an AbstractControl based on a {@code block}. <br>
         *
         * AbstractControls based on a {@code block} are the outermost AbstractControl (i.e., have no
         * {@link #encasingControl}), and may or may not later contain inner AbstractControls.
         * */
        public AbstractControl(HIRBlock block) {
            this.encasingControl = null;
            this.block = block;
            this.name = VeriOptIsabelleUtil.Syntax.toIsabelleString(block.toString().replace("B", "f"));
            this.blockPath = getBlockPath();
            this.phiIndexes = setIndexesForPhis();
        }

        /**
         * A constructor for building an AbstractControl based on a pre-existing ({@code original}) AbstractControl. <br>
         *
         * If {@code creatingInner}, then an inner AbstractControl is being created for the {@code original}
         * {@link #encasingControl}. Otherwise, a copy of the {@code original} is created.
         * */
        private AbstractControl(AbstractControl original, boolean creatingInner) {
            // The name & phi indexes are the same for both copies and inner AbstractControls
            this.phiIndexes = original.phiIndexes;
            this.name = original.name;

            // Set the remaining fields
            if (creatingInner) {
                this.encasingControl = original;
                this.block = null;
                this.blockPath = null;

                // LetExprs and LetNodes immediately store a reference to their inner controls
                if (original instanceof AbstractLetExpr outerLetExpr) {
                    outerLetExpr.inner = this;
                }

                if (original instanceof AbstractLetNode outerLetNode) {
                    outerLetNode.inner = this;
                }
            } else {
                this.encasingControl = original.encasingControl;
                this.block = original.block;
                this.blockPath = original.blockPath;
            }
        }

        /**
         * Returns the {@link #phiIndexes} for this AbstractControl.
         *
         * @return this AbstractControl's {@link #phiIndexes}.
         * */
        public HashMap<PhiNode, Integer> getPhiIndexes() {
            return phiIndexes;
        }

        /**
         * Generates and returns a mapping from each {@link PhiNode} which is used by a node in this {@link #blockPath},
         * to an index ranging on {@code [0...n]}, representing the index on which the Phi's value is passed to this
         * controlBlock as a parameter. <br>
         *
         * @return a mapping from each {@link PhiNode} used by a node in this {@link #block}, to an index on [0...n].
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
         * Generates and returns a list of {@link FixedNode}s representing the start-to-end path of the {@link #block}.
         *
         * @return a list of {@link FixedNode}s representing the path of this AbstractControl's {@link #block}.
         * */
        private ArrayList<FixedNode> getBlockPath() {
            // Extract the node path from the block
            ArrayList<FixedNode> path = new ArrayList<>();
            block.getNodes().iterator().forEachRemaining(path::add);

            if (path.size() > 3) {
                // TODO: For now, programs with more than 2 intermediate steps in their blocks will not be considered.
                throw new RuntimeException(String.format(exceptions.get("INTERMEDIATE_STEPS"), path.size() - 1, block));
            }

            // Return the path
            return path;
        }

        /**
         * Generates and returns a "Let" AbstractControl (i.e., an {@link AbstractLetNode} or an
         * {@link AbstractLetExpr}) representing the node at {@code blockPath[index]}.
         *
         * @param original the AbstractControl that the returned "Let" AbstractControl is based on.
         * @param blockPath the start-to-end path of the block that the returned AbstractControl represents a node in.
         * @param index a value such that {@code blockPath[index]} is the node the returned AbstractControl represents.
         * @return a "Let" AbstractControl to represent the node at {@code blockPath[index]}.
         * @throws RuntimeException if the node at {@code blockPath[index]} does not yet have a defined translation
         *                          rule.
         * */
        private static AbstractControl generateLetControl(AbstractControl original, ArrayList<FixedNode> blockPath,
                                                          int index, boolean creatingInner) {
            // Get the node at the step we're on
            FixedNode node = blockPath.get(index);

            // Prepare the outermost and innermost AbstractControl of this Let construct
            AbstractControl outermost = null;
            AbstractControl innermost = null;

            // Handle nodes which generate AbstractLetNodes
            if (node instanceof BytecodeExceptionNode) {
                AbstractLetNode bytecodeException = new AbstractLetNode(original, node, creatingInner);

                // Set the outermost & innermost AbstractControls
                outermost = bytecodeException;
                innermost = bytecodeException;
            }

            // Handle nodes which generate AbstractLetExprs
            if (node instanceof SignedRemNode || node instanceof SignedDivNode) {
                ValueNode x = ((FixedBinaryNode) node).getX();
                ValueNode y = ((FixedBinaryNode) node).getY();

                AbstractLetExpr lhsOperand = new AbstractLetExpr(original, x, creatingInner);
                AbstractLetExpr rhsOperand = new AbstractLetExpr(lhsOperand, y, true);
                AbstractLetNode innerLetNode = new AbstractLetNode(rhsOperand, node, true);

                // Set the outermost & innermost AbstractControls
                outermost = lhsOperand;
                innermost = innerLetNode;
            }

            // Finalise the AbstractControl block
            if (outermost != null && innermost != null) {
                // Generate the control structure for the next step in the path
                generateControl(innermost, blockPath, ++index, true);

                // Return the outermost AbstractControl
                return outermost;
            }

            // Node type isn't handled yet; shouldn't happen
            throw new RuntimeException(String.format(exceptions.get("UNHANDLED_LET"), node));
        }

        /**
         * Generates and returns an AbstractControl of the appropriate type based on the given {@code block}.
         *
         * @param block the block for which an AbstractControl is being generated.
         * @return the AbstractControl for the given {@code block}.
         * */
        public static AbstractControl generateControl(HIRBlock block) {
            // Create a base AbstractControl for the given block
            AbstractControl controlBlock = new AbstractControl(block);

            // Generate the AbstractControl for this block
            return generateControl(controlBlock, controlBlock.blockPath, 1, false);
        }

        /**
         * Generates and returns an AbstractControl of the appropriate type which represents the node at
         * {@code blockPath[index]}. If {@code creatingInner}, the provided {@code controlBlock} becomes the
         * {@link AbstractControl#encasingControl} of the returned AbstractControl.
         *
         * @param controlBlock the base (or outer) controlBlock for the AbstractControl being generated.
         * @param blockPath the start-to-end path of the block that the returned AbstractControl represents a node in.
         * @param index a value such that {@code blockPath[index]} is the node the returned AbstractControl represents.
         * @return the AbstractControl representing the node at {@code blockPath[index]}.
         * @throws RuntimeException if there were issues discerning or translating the particular AbstractControl type.
         * */
        private static AbstractControl generateControl(AbstractControl controlBlock, ArrayList<FixedNode> blockPath,
                                                       int index, boolean creatingInner) {
            // Get the node representing this step in the path
            FixedNode step = blockPath.get(index);

            // First, handle AbstractControls which never have trailing AbstractControls (Return, Call, If, Unwind)
            if (step instanceof ReturnNode returnNode) {
                // Block returns a value
                return new AbstractReturn(controlBlock, returnNode, creatingInner);
            }

            if (step instanceof LoopEndNode loopEndNode) {
                // Block calls a loop
                return new AbstractCall(controlBlock, loopEndNode, null, creatingInner);
            }

            if (step instanceof IfNode ifNode) {
                // Block is a loop header or a standard If block
                return new AbstractIf(controlBlock, ifNode, creatingInner);
            }

            if (step instanceof EndNode end) {
                // Block calls its successor
                return new AbstractCall(controlBlock, end, null, creatingInner);
            }

            if (step instanceof UnwindNode unwindNode) {
                // Block unwinds an exception
                if (controlBlock instanceof AbstractLetNode outer && outer.node.equals(unwindNode.exception())) {
                    return new AbstractUnwind(outer, unwindNode, creatingInner);
                }

                // We currently don't know how to handle Unwinds which aren't preceded by their Exception node
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // Handle AbstractControls which have trailing AbstractControls (LetNode, LetExpr)
            return generateLetControl(controlBlock, blockPath, index, creatingInner);
        }
    }

    /**
     * Represents an Isabelle AbstractCall structure
     * */
    private static class AbstractCall extends AbstractControl {

        // The AbstractControl block that this Call invokes
        private AbstractControl invoking;

        // The parameters being passed in the call
        private final ArrayList<Node> parameters = new ArrayList<>();

        // The final node of the call block
        private final FixedNode endNode;

        /**
         * Base constructor for an AbstractCall
         * */
        public AbstractCall(AbstractControl original, FixedNode endNode, AbstractControl invokes,
                            boolean creatingInner) {
            super(original, creatingInner);
            this.endNode = endNode;
            this.invoking = invokes;
        }

        /**
         * A constructor for building an inner AbstractCall within an {@code outer} AbstractControl, which
         * {@code invokes} the provided AbstractControl.
         * */
        public AbstractCall(AbstractControl outer, AbstractControl invokes) {
            this(outer, null, invokes, true);
        }
    }

    /**
     * Represents an Isabelle AbstractReturn structure
     * */
    private static class AbstractReturn extends AbstractControl {

        // The ReturnNode this AbstractControl represents
        private final ReturnNode returnNode;

        /**
         * A base constructor for an AbstractReturn
         * */
        public AbstractReturn(AbstractControl original, ReturnNode returnNode, boolean creatingInner) {
            super(original, creatingInner);
            this.returnNode = returnNode;
        }
    }

    /**
     * Represents an Isabelle AbstractIf structure
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
         * A base constructor for an AbstractIf
         * */
        public AbstractIf(AbstractControl original, IfNode ifNode, boolean creatingInner) {
            super(original, creatingInner);
            this.ifNode = ifNode;
        }

        /**
         * Sets the {@link #trueBranch} to be an inner AbstractCall, {@code invoking} the given AbstractControl.
         *
         * @param invoking the AbstractControl that this If's {@link #trueBranch} calls.
         * */
        private void setTrueBranch(AbstractControl invoking) {
            this.trueBranch = new AbstractCall(this, invoking);
        }

        /**
         * Sets the {@link #falseBranch} to be an inner AbstractCall, {@code invoking} the given AbstractControl.
         *
         * @param invoking the AbstractControl that this If's {@link #falseBranch} calls.
         * */
        private void setFalseBranch(AbstractControl invoking) {
            this.falseBranch = new AbstractCall(this, invoking);
        }
    }

    /**
     * Represents an Isabelle AbstractLetExpr structure
     * */
    private static class AbstractLetExpr extends AbstractControl {

        // The value of the expression
        private final ValueNode value;

        // This AbstractLetExpr's nested AbstractControl
        private AbstractControl inner;

        /**
         * A base constructor for an AbstractLetExpr
         * */
        public AbstractLetExpr(AbstractControl original, ValueNode value, boolean creatingInner) {
            super(original, creatingInner);
            this.value = value;
        }
    }

    /**
     * Represents an Isabelle AbstractLetNode structure
     * */
    private static class AbstractLetNode extends AbstractControl {

        // The node being stored
        private final Node node;

        // This AbstractLetNode's nested AbstractControl
        private AbstractControl inner;

        /**
         * A base constructor for an AbstractLetNode
         * */
        public AbstractLetNode(AbstractControl original, Node node, boolean creatingInner) {
            super(original, creatingInner);
            this.node = node;
        }
    }

    /**
     * Represents an Isabelle AbstractUnwind structure
     * */
    private static class AbstractUnwind extends AbstractControl {

        // The UnwindNode this AbstractUnwind represents
        private final UnwindNode unwind;

        /**
         * A base constructor for an AbstractUnwind
         * */
        public AbstractUnwind(AbstractControl original, UnwindNode unwind, boolean creatingInner) {
            super(original, creatingInner);
            this.unwind = unwind;
        }
    }

    /**
     * Handles the processing of AbstractControls
     * */
    private static final class AbstractControlProcessor {

        // A mapping from blocks to their corresponding AbstractControl
        private final HashMap<HIRBlock, AbstractControl> controlBlocks;

        public AbstractControlProcessor(HashMap<HIRBlock, AbstractControl> blocks) {
            controlBlocks = blocks;
        }

        /**
         * Processes the given {@code block} by storing information relevant to the Isabelle translation in its
         * corresponding AbstractControl.
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
         * @param controlBlock the AbstractControl being processed.
         * */
        private void processAbstractControl(AbstractControl controlBlock) {
            // Process the AbstractControl based on its type
            if (controlBlock instanceof AbstractCall abstractCall) {
                processCall(abstractCall);
            } else if (controlBlock instanceof AbstractIf abstractIf) {
                processIf(abstractIf);
            } else if (controlBlock instanceof AbstractLetExpr abstractLetExpr) {
                processAbstractControl(abstractLetExpr.inner);
            } else if (controlBlock instanceof AbstractLetNode abstractLetNode) {
                processAbstractControl(abstractLetNode.inner);
            } else if (controlBlock instanceof AbstractReturn || controlBlock instanceof AbstractUnwind) {
                // We already have all the information we need for encoding; do nothing.
                return;
            } else {
                // Shouldn't get here
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }
        }

        /**
         * Processes the given {@code controlBlock} as an If.
         *
         * @param controlBlock the AbstractIf being processed.
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
                    // This is an inner control, and hence does not have a defined blockPath. Shouldn't happen.
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
         * Populates the given {@code controlBlock}s true or false branch's {@link AbstractCall#parameters} with the
         * parameters being passed into the Call. The branch whose parameters are being updated is dependent on the
         * given {@code branchType}.
         *
         * @param controlBlock the AbstractIf being processed.
         * @param branchType determines which of the {@code controlBlock}'s branches (true or false) are having their
         *                   {@link AbstractCall#parameters} extended.
         * @throws RuntimeException if the AbstractIf's relevant branch is null, or there were issues translating the
         *         phis.
         * */
        private void addIfBranchCallParameters(AbstractIf controlBlock, boolean branchType) {
            // Get the expected branch
            AbstractCall branch = (branchType) ? controlBlock.trueBranch : controlBlock.falseBranch;

            if (branch == null) {
                // No branch of the given type has been set for this If
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            for (PhiNode phi : branch.invoking.phiIndexes.keySet()) {
                if (phi.valueCount() != 2 || controlBlock.getPhiIndexes().get(phi) == null) {
                    // We can only translate phis with two inputs, and we expect that the branch's phis are known to us
                    throw new RuntimeException(String.format(exceptions.get("PHI_INPUTS"), phi.valueCount()));
                }

                // We need to call the block with the phis at the index that they expect
                branch.parameters.add(branch.invoking.phiIndexes.get(phi), phi);
            }
        }

        /**
         * Helper method which determines how the given {@code controlBlock} should be processed based on the type of
         * its {@link AbstractCall#endNode}.
         *
         * @param controlBlock the AbstractCall block being processed.
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
         * controlBlocks of this structure may only have a single successor (for now). Thus, they simply call that
         * successor with any expected phi values.
         *
         * @param controlBlock the AbstractCall being processed.
         * @param endNode the final node of the block represented by the given {@code controlBlock}.
         * @throws RuntimeException if there were issues processing the {@code controlBlock}.
         * */
        private void processCall(AbstractCall controlBlock, EndNode endNode) {
            // Extract the block
            HIRBlock block = ((AbstractControl) controlBlock).block;

            if (block.getSuccessorCount() != 1) {
                // This block has more than one or no successors; can't translate
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // Finalise the call
            finaliseCall(controlBlock, controlBlocks.get(block.getFirstSuccessor()), 0);
        }

        /**
         * Processes the given {@code controlBlock} as a Call, whose {@code endNode} is a {@link LoopEndNode}. <br>
         *
         * {@code controlBlock}s of this structure simply call the loop header, passing in the changes made inside the
         * loop.
         *
         * @param controlBlock the AbstractCall being processed.
         * @param endNode the final node of the block represented by the given {@code controlBlock}.
         * */
        private void processCall(AbstractCall controlBlock, LoopEndNode endNode) {
            // Get the AbstractControl for the loop that this block calls
            AbstractControl loopBlock = null;

            // Iterate through each block looking for the loop being called
            for (AbstractControl control : controlBlocks.values()) {
                if (control.blockPath == null || control.block == null) {
                    // This is an inner control, and hence does not have a defined blockPath or block. Shouldn't happen.
                    continue;
                }

                if (control.block.isLoopHeader() && control.blockPath.getFirst() == endNode.loopBegin()) {
                    loopBlock = control;
                    break;
                }
            }

            // Finalise the call
            finaliseCall(controlBlock, loopBlock, 1);
        }

        /**
         * Finalises the given {@code callBlock} by storing the AbstractControl being invoked and extending the call
         * parameters.
         *
         * @param callBlock the callBlock being finalised.
         * @param invoking the AbstractControl being invoked by the given {@code callBlock}.
         * @param phiIndex the index of the input to any phis that this {@code callBlock} represents.
         * @throws RuntimeException if the given AbstractControl being invoked is null.
         * */
        private void finaliseCall(AbstractCall callBlock, AbstractControl invoking, int phiIndex) {
            if (invoking == null) {
                // Ensure that the block being invoked was found in our controlBlocks
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // Store the block being called
            callBlock.invoking = invoking;

            // Get the parameters being passed into the call
            addParameterPhis(callBlock, phiIndex);
        }

        /**
         * Extend the call {@link AbstractCall#parameters} of the given {@code controlBlock} to include any phis
         * expected by the AbstractControl this {@code controlBlock} is {@link AbstractCall#invoking}.
         *
         * @param controlBlock the AbstractCall whose parameters are being updated.
         * @param phiIndex the index of the phi's input values that the given {@code controlBlock} represents.
         *                 As we are currently only handling phis with two inputs, the {@code controlBlock} is either:
         *                      - the loops' predecessor, providing the initial value for the phi (0)
         *                      - the body of the loop, providing the updated value (1)
         * @throws RuntimeException if {@link AbstractCall#invoking} is an inner AbstractControl, or there were issues
         *                          translating the phis.
         * */
        private void addParameterPhis(AbstractCall controlBlock, int phiIndex) {
            if (controlBlock.invoking.blockPath == null) {
                // The control being invoked is an inner control; shouldn't happen.
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // Get the initial node of the block being invoked
            Node start = controlBlock.invoking.blockPath.getFirst();

            // Iterate over every phi expected by the block being invoked
            for (PhiNode phi : controlBlock.invoking.phiIndexes.keySet()) {
                if (phi.valueCount() != 2 || phi.merge() != start || !(phi.merge() instanceof LoopBeginNode)) {
                    // We can only translate phis with two inputs, and we expect the phis merge to be a loop
                    throw new RuntimeException(String.format(exceptions.get("PHI_INPUTS"), phi.valueCount()));
                }

                // Our caller should call the loop with the phis in their expected places
                controlBlock.parameters.add(controlBlock.invoking.phiIndexes.get(phi), phi.valueAt(phiIndex));
            }
        }
    }

    /**
     * Represents an Isabelle AbstractProgram
     * */
    private static final class AbstractProgram {

        /**
         * Defines nodes which result in or utilise a {@code LetNode} or {@code LetExpr} AbstractControl construct
         * whose encoding is not currently handled. <br>
         *
         * In general, nodes that generate or interact with a LetNode or LetExpr construct are those that alter or
         * access the Isabelle heap.
         * */
        private static final ArrayList<Class<? extends Node>> UnhandledLetControlNodes = new ArrayList<>();
        static {
            UnhandledLetControlNodes.add(ArrayLengthNode.class);
            UnhandledLetControlNodes.add(LoadFieldNode.class);
            UnhandledLetControlNodes.add(LoadIndexedNode.class);
            UnhandledLetControlNodes.add(NewArrayNode.class);
            UnhandledLetControlNodes.add(NewInstanceNode.class);
            UnhandledLetControlNodes.add(StoreFieldNode.class);
            UnhandledLetControlNodes.add(StoreIndexedNode.class);
        }

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
         * Defines the Isabelle format of each AbstractControl structure.
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
            controlFormats.put(AbstractIf.class,      "(If (%s)\n\t%s\n\t%s)");
            controlFormats.put(AbstractLetNode.class, "(LetNode %s %s\n\t\t%s)");
            controlFormats.put(AbstractLetExpr.class, "(LetExpr %s (%s)\n\t\t%s)");
            controlFormats.put(AbstractUnwind.class,  "(Unwind %s)");
        }

        /**
         * Defines the Isabelle format for different sections of the AbstractProgram.
         * */
        private static final HashMap<String, String> programSections = new HashMap<>();
        static {
            programSections.put("HEADER",
                    "\n\ndefinition {name}_functional :: AbstractProgram where\n  \"{name}_functional = Map.empty (\n");
            programSections.put("FUNCTION_HEADER", "    %s \\<mapsto> ");
            programSections.put("FUNCTION_FOOTER", ",\n");
            programSections.put("FOOTER",          "  )\"\n");
        }

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
         * Generates the Isabelle {@code AbstractProgram} and stores it in {@link #program}, before returning this
         * object.
         *
         * @return this {@link AbstractProgram} with its Isabelle AbstractProgram encoding stored in {@link #program}.
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
         * */
        private void encodeProgram() {
            // Open the program
            program.append(programSections.get("HEADER"));

            // We need to increment the phi indexes to account for parameters passed into the program
            incrementPhis();

            // Encode each AbstractControl block
            for (HIRBlock block : blocks) {
                // Get the controlBlock
                AbstractControl controlBlock = controlBlocks.get(block);

                // Begin the function definition
                program.append(programSections.get("FUNCTION_HEADER").replace("%s", controlBlock.name));

                // Add the corresponding AbstractControl definition
                String format = encodeControlBlockFormat(controlBlock);
                String[] arguments = encodeControlBlockArguments(controlBlock).toArray(new String[0]);
                program.append(VeriOptIsabelleUtil.StringFormatting.formatPlaceholderString(format, arguments));

                // Close the function definition
                program.append(programSections.get("FUNCTION_FOOTER"));
            }

            // Close the program
            VeriOptIsabelleUtil.StringFormatting.removeLastInstanceOfSymbol(program, ",");
            program.append(programSections.get("FOOTER"));
        }

        /**
         * Increments the parameter indexes stored by all PhiNodes ({@link AbstractControl#phiIndexes}) which, upon
         * translation, become ParameterExprs to a function block. <br>
         *
         * This incrementation 'shifts' the function block parameters forwards to allow the program's parameters to
         * maintain their original parameter index, which simplifies translation and simulates program scope by allowing
         * any AbstractControl to access the program's parameters on a common index.
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
         * @param controlBlock the controlBlock whose encoding format is being generated.
         * @return the encoding format for the given {@code controlBlock}.
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

            // LetExpr controls are extended by their inner control's encoding
            if (controlBlock instanceof AbstractLetExpr abstractLetExpr) {
                String innerFormat = encodeControlBlockFormat(abstractLetExpr.inner);

                // Add the inner format encoding to the end
                return String.format(controlFormats.get(abstractLetExpr.getClass()), "%s", "%s", innerFormat);
            }

            // LetNode controls are extended by their inner control's encoding
            if (controlBlock instanceof AbstractLetNode abstractLetNode) {
                String innerFormat = encodeControlBlockFormat(abstractLetNode.inner);

                // Add the inner format encoding to the end
                return String.format(controlFormats.get(abstractLetNode.getClass()), "%s", "%s", innerFormat);
            }

            // Shouldn't happen
            return "";
        }

        /**
         * Encodes and returns the arguments which will replace the placeholders in the given {@code controlBlock}'s
         * {@link #encodeControlBlockFormat(AbstractControl)}, to produce its Isabelle definition.
         *
         * @param controlBlock the AbstractControl whose arguments are being encoded.
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
                arguments.add(abstractCall.invoking.name);

                // Generate and add the parameters to the call
                ArrayList<String> encodedParameters = VeriOptIsabelleUtil.encodeIRExprs(abstractCall.parameters,
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
                arguments.addAll(encodeControlBlockArguments(abstractLetNode.inner));
            }

            // LetExpr controls must encode their node's ID and its IRExpr, and any trailing controls
            if (controlBlock instanceof AbstractLetExpr abstractLetExpr) {
                arguments.add(VeriOptIsabelleUtil.asNodeID(abstractLetExpr.value));
                arguments.add(VeriOptIsabelleUtil.encodeIRExpr(abstractLetExpr.value, true, phis));
                arguments.addAll(encodeControlBlockArguments(abstractLetExpr.inner));
            }

            // Unwind controls must encode their node's ID
            if (controlBlock instanceof AbstractUnwind abstractUnwind) {
                arguments.add(VeriOptIsabelleUtil.asNodeID(abstractUnwind.unwind.exception()));
            }

            return arguments;
        }

        /**
         * Creates the AbstractControl structures corresponding to the {@link #controlFlowGraph}'s HIRBlocks, and
         * processes them to extract the information necessary for translation.
         * */
        private void processAbstractControls() {
            // Map each HIRBlock to its corresponding AbstractControl
            for (HIRBlock block : blocks) {
                controlBlocks.put(block, AbstractControl.generateControl(block));
            }

            // Create the AbstractControl processor
            AbstractControlProcessor processor = new AbstractControlProcessor(controlBlocks);

            // Process all the AbstractControls from end-to-start
            for (int i = blocks.length - 1; i >= 0; i--) {
                processor.processControlBlock(blocks[i]);
            }
        }

        /**
         * Returns whether this AbstractProgram can be encoded into an Isabelle AbstractProgram.
         *
         * @return {@code true} if this AbstractProgram can be encoded.
         * @throws RuntimeException if this AbstractProgram cannot be encoded, with an accompanying reason.
         * */
        private boolean canBeEncoded() {
            Node undefinedNode = getUndefinedNode();
            if (undefinedNode != null) {
                // At least one of the nodes in the graph does not have a corresponding Isabelle definition
                throw new RuntimeException(String.format(exceptions.get("UNDEFINED_NODE"), undefinedNode));
            }

            Node unhandledLetNode = getUnhandledLetNode();
            if (unhandledLetNode != null) {
                // The graph contains a node which requires a LetExpr or LetNode AbstractControl that isn't yet handled
                throw new RuntimeException(String.format(exceptions.get("UNHANDLED_LET"), unhandledLetNode));
            }

            if (isInvoking()) {
                // Currently, AbstractPrograms cannot perform method invocation
                throw new RuntimeException(exceptions.get("INVOKING"));
            }

            // If we got here, no exception occurred
            return true;
        }

        /**
         * Returns whether this AbstractProgram invokes another method. The nodes which signify a method invocation are
         * defined in {@link #interproceduralNodes}.
         *
         * @return {@code true} if this AbstractProgram invokes another method, else {@code false}.
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
         * Returns a {@link Node} in this AbstractProgram's {@link #controlFlowGraph} which will require LetNode or
         * LetExpr AbstractControls (whose encoding is currently not handled) in its Isabelle definition. <br>
         *
         * The nodes for which this is true are defined in {@link #UnhandledLetControlNodes}.
         *
         * @return a {@link Node} which will require LetNode or LetExpr AbstractControls whose encodings are not
         *         handled, if one exists in the {@link #controlFlowGraph}, else {@code null}.
         * */
        private Node getUnhandledLetNode() {
            for (Node node : controlFlowGraph.graph.getNodes()) {
                if (UnhandledLetControlNodes.contains(node.getClass())) {
                    return node;
                }
            }
            return null;
        }

        /**
         * Returns a {@link Node} in this AbstractProgram's {@link #controlFlowGraph} which does not currently have an
         * Isabelle definition, if one exists. <br>
         *
         * Nodes which have an Isabelle definition will return {@code true} for
         * {@link VeriOptGraphTranslator#isInIrNodes(Node)}.
         *
         * @return a {@link Node} which does not have a corresponding Isabelle definition, if one exists in the
         *         {@link #controlFlowGraph}, else {@code null}.
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
