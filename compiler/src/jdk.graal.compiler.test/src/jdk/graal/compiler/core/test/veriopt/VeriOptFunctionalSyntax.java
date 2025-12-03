package jdk.graal.compiler.core.test.veriopt;

import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.core.veriopt.VeriOptGraphTranslator;
import jdk.graal.compiler.core.veriopt.VeriOptIsabelleUtil;
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

        // Indicates that this AbstractControl block has been processed and is ready to be encoded
        private boolean completed = false;

        public AbstractControl(HIRBlock block) {
            this.block = block;
            this.name = VeriOptIsabelleUtil.Syntax.toIsabelleString(block.toString().replace("B", "f"));
            this.blockPath = getBlockPath();
            this.phiIndexes = setIndexesForPhis();
        }

        public AbstractControl(AbstractControl copy) {
            this.phiIndexes = copy.phiIndexes;
            this.block = copy.block;
            this.blockPath = copy.blockPath;
            this.name = copy.name;
            this.completed = copy.completed;
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
         * @return the list of {@link FixedNode} representing the path of this AbstractControl's {@link #block}.
         * */
        private ArrayList<FixedNode> getBlockPath() {
            // Extract the node path from the block
            ArrayList<FixedNode> path = new ArrayList<>();
            block.getNodes().iterator().forEachRemaining(path::add);

            if (path.size() != 2) {
                // TODO: For now, programs with intermediate steps in their blocks will not be considered.
                throw new RuntimeException(String.format(exceptions.get("INTERMEDIATE_STEPS"), path.size() - 1, block));
            }

            // Return the path
            return path;
        }

        /**
         * Indicate that this {@code AbstractControl} has been fully processed.
         * */
        public void markCompleted() {
            this.completed = true;
        }

        /**
         * Generates and returns an AbstractControl of the appropriate type based on the given block.
         *
         * @param block the block for which an AbstractControl is being generated.
         * @return the AbstractControl for the given block.
         * @throws RuntimeException if the particular AbstractControl type cannot be discerned for this block.
         * */
        public static AbstractControl generateControl(HIRBlock block) {
            // Create a base AbstractControl for the given block
            AbstractControl controlBlock = new AbstractControl(block);

            // Determine the type of the block based on its path's characteristics
            FixedNode endNode = controlBlock.blockPath.getLast();

            if (endNode instanceof ReturnNode returnNode) {
                // Block returns a value
                return new AbstractReturn(controlBlock, returnNode);
            }

            if (endNode instanceof LoopEndNode loopEndNode) {
                // Block calls a loop
                return new AbstractCall(controlBlock, loopEndNode);
            }

            if (block.isLoopHeader() && endNode instanceof IfNode ifNode) {
                // Block is a loop header
                return new AbstractIf(controlBlock, ifNode);
            }

            if (endNode instanceof EndNode end) {
                // Block calls its successor
                return new AbstractCall(controlBlock, end);
            }

            // We currently don't know how to represent this block
            throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
        }
    }

    /**
     * Represents an Isabelle AbstractCall structure
     * */
    private static class AbstractCall extends AbstractControl {

        // The name of the AbstractControl block that this Call invokes
        private String invokingName;

        // The parameters being passed in the call
        private final ArrayList<Node> parameters = new ArrayList<>();

        // The final node of the call block
        private final FixedNode endNode;

        public AbstractCall(AbstractControl copy, FixedNode endNode) {
            super(copy);
            this.endNode = endNode;
        }
    }

    /**
     * Represents an Isabelle AbstractReturn structure
     * */
    private static class AbstractReturn extends AbstractControl {

        // The value being returned by this Return
        private ValueNode returnValue;

        // The ReturnNode this AbstractControl represents
        private final ReturnNode endNode;

        public AbstractReturn(AbstractControl copy, ReturnNode returnNode) {
            super(copy);
            this.endNode = returnNode;
        }
    }

    /**
     * Represents an Isabelle AbstractIf structure
     * */
    private static class AbstractIf extends AbstractControl {

        // The condition for the If
        private LogicNode condition;

        // The AbstractControl being called on the true branch
        private AbstractControl trueBranch;

        // The AbstractControl being called on the false branch
        private AbstractControl falseBranch;

        // The parameters to pass to the true branch call
        private final ArrayList<Node> trueParameters = new ArrayList<>();

        // The parameters to pass to the false branch call
        private final ArrayList<Node> falseParameters = new ArrayList<>();

        // The IfNode this AbstractControl represents
        private final IfNode ifNode;

        public AbstractIf(AbstractControl copy, IfNode ifNode) {
            super(copy);
            this.ifNode = ifNode;
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
         * corresponding AbstractControl, if this {@code block} hasn't yet been processed.
         *
         * @param block the block being processed.
         * */
        private void processControlBlock(HIRBlock block) {
            // Get the AbstractControl for this block
            AbstractControl controlBlock = controlBlocks.get(block);

            if (controlBlock.completed) {
                // Already processed, do nothing
                return;
            }

            // Process the block
            processFromEnd(controlBlock.blockPath.getLast(), controlBlock);
        }

        /**
         * Processes the given {@code controlBlock} based on its type.
         *
         * @param controlBlock the AbstractControl being processed.
         * */
        private void processFromEnd(FixedNode endNode, AbstractControl controlBlock) {
            // Process the particular AbstractControl based on the given controlBlock's type
            if (controlBlock instanceof AbstractReturn abstractReturn) {
                processReturnExpr(abstractReturn);
                return;
            }

            if (controlBlock instanceof AbstractCall abstractCall) {
                processCall(abstractCall);
                return;
            }

            if (controlBlock instanceof AbstractIf abstractIf) {
                processIf(abstractIf);
            }
        }

        /**
         * Processes the given {@code controlBlock} as an If.
         *
         * @param controlBlock the AbstractControl being processed.
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
                if (control.blockPath.getFirst() == trueStart) {
                    controlBlock.trueBranch = control;
                }

                if (control.blockPath.getFirst() == falseStart) {
                    controlBlock.falseBranch = control;
                }
            }

            if (controlBlock.trueBranch == null || controlBlock.falseBranch == null) {
                // True or false branch was not found in CFG blocks
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            // We need to determine what is being passed to the trueBranch & falseBranch calls
            addIfBranchCallParameters(controlBlock, true);
            addIfBranchCallParameters(controlBlock, false);

            // Mark this block as completed
            controlBlock.markCompleted();
        }

        /**
         * Populates the given {@code controlBlock}s parameter list (either {@link AbstractIf#trueParameters} or
         * {@link AbstractIf#falseParameters}) with the parameters being passed into the true or false branch call,
         * respectively. The parameter list being updated is dependent on the given {@code branchType}.
         *
         * @param controlBlock the AbstractControl being processed.
         * @param branchType determines which of the {@code controlBlock}'s branches (true or false) are having their
         *                   parameters extended.
         * @throws RuntimeException if the AbstractIf's relevant branch is null, or there were issues translating the
         *         phis.
         * */
        private void addIfBranchCallParameters(AbstractIf controlBlock, boolean branchType) {
            // Get the expected branch and its parameter list
            AbstractControl invoking = (branchType) ? controlBlock.trueBranch : controlBlock.falseBranch;
            ArrayList<Node> parameters = (branchType) ? controlBlock.trueParameters : controlBlock.falseParameters;

            if (invoking == null) {
                // No branch of the given type has been set for this If
                throw new RuntimeException(exceptions.get("CANNOT_TRANSLATE"));
            }

            for (PhiNode phi : invoking.phiIndexes.keySet()) {
                if (phi.valueCount() != 2 || controlBlock.getPhiIndexes().get(phi) == null) {
                    // We can only translate phis with two inputs, and we expect that the branch's phis are known to us
                    throw new RuntimeException(String.format(exceptions.get("PHI_INPUTS"), phi.valueCount()));
                }

                // We need to call the block with the phis at the index that they expect
                parameters.add(invoking.phiIndexes.get(phi), phi);
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
            }

            if (endNode instanceof EndNode end) {
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
         * controlBlocks of this structure simply call the loop header, passing in the changes made inside the loop.
         *
         * @param controlBlock the AbstractCall being processed.
         * @param endNode the final node of the block represented by the given {@code controlBlock}.
         * */
        private void processCall(AbstractCall controlBlock, LoopEndNode endNode) {
            // Get the AbstractControl for the loop that this block calls
            AbstractControl loopBlock = null;

            // Iterate through each block looking for the loop being called
            for (AbstractControl control : controlBlocks.values()) {
                if (control.block.isLoopHeader() && control.blockPath.getFirst() == endNode.loopBegin()) {
                    loopBlock = control;
                    break;
                }
            }

            // Finalise the call
            finaliseCall(controlBlock, loopBlock, 1);
        }

        /**
         * Finalises the given {@code callBlock} by storing the name of the AbstractControl being invoked, extending
         * the call parameters, and marking it as completed.
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

            // Store the name of the block being called
            callBlock.invokingName = invoking.name;

            // Get the parameters being passed into the call
            addParameterPhis(callBlock, invoking, phiIndex);

            // Mark this block as completed
            callBlock.markCompleted();
        }

        /**
         * Extend the call {@link AbstractCall#parameters} of the given {@code controlBlock} to include any phis
         * expected by the controlBlock being invoked ({@code invoking}).
         *
         * @param controlBlock the AbstractCall whose parameters are being updated.
         * @param invoking the AbstractControl being invoked by the given {@code controlBlock}.
         * @param phiIndex the index of the phi's input values that the given {@code controlBlock} represents.
         *                 As we are currently only handling phis with two inputs, the {@code controlBlock} is either:
         *                      - the loops' predecessor, providing the initial value for the phi (0)
         *                      - the body of the loop, providing the updated value (1)
         * @throws RuntimeException if there were issues translating the phis.
         * */
        private void addParameterPhis(AbstractCall controlBlock, AbstractControl invoking, int phiIndex) {
            // Get the initial node of the block being invoked
            Node start = invoking.blockPath.getFirst();

            // Iterate over every phi expected by the block being invoked
            for (PhiNode phi : invoking.phiIndexes.keySet()) {
                if (phi.valueCount() != 2 || phi.merge() != start || !(phi.merge() instanceof LoopBeginNode)) {
                    // We can only translate phis with two inputs, and we expect the phis merge to be a loop
                    throw new RuntimeException(String.format(exceptions.get("PHI_INPUTS"), phi.valueCount()));
                }

                // Our caller should call the loop with the phis in their expected places
                controlBlock.parameters.add(invoking.phiIndexes.get(phi), phi.valueAt(phiIndex));
            }
        }

        /**
         * Processes the given {@code controlBlock} as a ReturnExpr.
         *
         * @param controlBlock the AbstractReturn being processed.
         * */
        private void processReturnExpr(AbstractReturn controlBlock) {
            // Store the return value
            controlBlock.returnValue = (controlBlock.endNode.result());

            // Mark this block as completed
            controlBlock.markCompleted();
        }
    }

    /**
     * Represents an Isabelle AbstractProgram
     * */
    private static final class AbstractProgram {

        /**
         * Defines nodes which result in a LetNode or LetExpr AbstractControl construct. <br>
         *
         * In general, these are any nodes that alter or access the Isabelle heap.
         * */
        private static final ArrayList<Class<? extends Node>> letControlNodes = new ArrayList<>();
        static {
            letControlNodes.add(StoreFieldNode.class);
            letControlNodes.add(LoadFieldNode.class);
            letControlNodes.add(BytecodeExceptionNode.class);
            letControlNodes.add(UnwindNode.class);
            letControlNodes.add(NewArrayNode.class);
            letControlNodes.add(StoreIndexedNode.class);
            letControlNodes.add(LoadIndexedNode.class);
            letControlNodes.add(NewInstanceNode.class);
            letControlNodes.add(ArrayLengthNode.class);
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
         *     AbstractReturn -> (ReturnExpr (%s))
         *     AbstractCall -> (Call %s %s)
         *     AbstractIf -> (If (%s) %s %s)
         * </pre>
         * */
        private static final HashMap<Class<? extends AbstractControl>, String> controlFormats = new HashMap<>();
        static {
            controlFormats.put(AbstractReturn.class, "(ReturnExpr (%s))");
            controlFormats.put(AbstractCall.class,   "(Call %s %s)");
            controlFormats.put(AbstractIf.class,     "(If (%s)\n\t%s\n\t%s)");
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
                program.append(VeriOptIsabelleUtil.StringFormatting.formatPlaceholderString(
                        controlFormats.get(controlBlock.getClass()),
                        encodeControlBlockArguments(controlBlock).toArray(new String[0])));

                // Close the function definition
                program.append(programSections.get("FUNCTION_FOOTER"));
            }

            // Close the program
            VeriOptIsabelleUtil.StringFormatting.removeLastInstanceOfSymbol(program, ",");
            program.append(programSections.get("FOOTER"));
        }

        /**
         * Increments the parameter indexes stored by all PhiNodes {@link AbstractControl#phiIndexes} which, upon
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
         * Encodes and returns the arguments which will replace the placeholders in the given {@code controlBlock}'s
         * {@link #controlFormats}, to produce its Isabelle definition.
         *
         * @param controlBlock the AbstractControl whose arguments are being encoded.
         * @return the arguments for the given {@code controlBlock} in their Isabelle syntax.
         * */
        private ArrayList<String> encodeControlBlockArguments(AbstractControl controlBlock) {
            ArrayList<String> arguments = new ArrayList<>();

            // Get the expected indexes of this controlBlock's phis
            HashMap<PhiNode, Integer> phis = controlBlock.getPhiIndexes();

            // Return controls simply encode the expression they're returning
            if (controlBlock instanceof AbstractReturn abstractReturn) {
                arguments.add(VeriOptIsabelleUtil.encodeIRExpr(abstractReturn.returnValue, true, phis));
            }

            // Call controls must encode the name of the function they're calling, and the arguments being passed in
            if (controlBlock instanceof AbstractCall abstractCall) {
                // Add the name of the block being called
                arguments.add(abstractCall.invokingName);

                // Generate and add the parameters to the call
                ArrayList<String> encodedParameters = VeriOptIsabelleUtil.encodeIRExprs(abstractCall.parameters,
                        true, phis);
                arguments.add(VeriOptIsabelleUtil.Syntax.toIsabelleArray(encodedParameters));
            }

            // If nodes must encode their condition and a call to their true & false branches
            if (controlBlock instanceof AbstractIf abstractIf) {
                // Get the if condition
                String encodedCondition = VeriOptIsabelleUtil.encodeIRExpr(abstractIf.condition, true, phis);

                // Generate the parameters to the true and false branch calls
                ArrayList<String> trueParameters = VeriOptIsabelleUtil.encodeIRExprs(abstractIf.trueParameters,
                        true, phis);
                ArrayList<String> falseParameters = VeriOptIsabelleUtil.encodeIRExprs(abstractIf.falseParameters,
                        true, phis);

                // Get the Call headers for the true & false branches
                String callHeader = controlFormats.get(AbstractCall.class);

                // Encode the true & false branch calls
                String trueCall = VeriOptIsabelleUtil.StringFormatting.formatPlaceholderString(callHeader,
                        abstractIf.trueBranch.name, VeriOptIsabelleUtil.Syntax.toIsabelleArray(trueParameters));
                String falseCall = VeriOptIsabelleUtil.StringFormatting.formatPlaceholderString(callHeader,
                        abstractIf.falseBranch.name, VeriOptIsabelleUtil.Syntax.toIsabelleArray(falseParameters));

                // Add the condition, trueBranch & falseBranch encodings for the If
                arguments.add(encodedCondition);
                arguments.add(trueCall);
                arguments.add(falseCall);
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

            // Continue processing the AbstractControls until they are all completed
            while (!allAbstractControlsCompleted()) {
                for (int i = blocks.length - 1; i >= 0; i--) {
                    // Process the blocks from end-to-start
                    processor.processControlBlock(blocks[i]);
                }
            }
        }

        /**
         * Returns whether all the AbstractProgram's AbstractControls ({@link #controlBlocks}) have been processed and
         * completed.
         *
         * @return {@code true} if all the AbstractControls have been completed, else {@code false}.
         * */
        private boolean allAbstractControlsCompleted() {
            for (AbstractControl controlBlock : controlBlocks.values()) {
                if (!controlBlock.completed) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns whether this {@code AbstractProgram} can be encoded into an Isabelle AbstractProgram.
         *
         * @return {@code true} if this AbstractProgram can be encoded.
         * @throws RuntimeException if this AbstractProgram cannot be encoded, with an accompanying reason.
         * */
        private boolean canBeEncoded() {
            Node undefinedNode = getUndefinedNode();
            if (undefinedNode != null) {
                // At least one of the nodes in the graph does not have a corresponding Isabelle definition
                String message = "the graph contains a node (%s) which isn't in -Duq.irnodes=file.";
                throw new RuntimeException(String.format(message, undefinedNode));
            }

            if (requiresLets()) {
                // Currently, we're not encoding LetExpr or LetNode AbstractControls
                throw new RuntimeException("the AbstractProgram requires LetExpr or LetNode AbstractControls.");
            }

            if (isInvoking()) {
                // Currently, AbstractPrograms cannot perform method invocation
                throw new RuntimeException("the AbstractProgram cannot invoke another method.");
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
         * Returns whether this {@code AbstractProgram} will require LetNode or LetExpr AbstractControls in its Isabelle
         * definition. The nodes which necessitate LetNode or LetExpr AbstractControls are defined in
         * {@link #letControlNodes}.
         *
         * @return {@code true} if this {@code AbstractProgram} will require LetNode or LetExpr AbstractControls, else
         *         {@code false}.
         * */
        private boolean requiresLets() {
            for (Node node : controlFlowGraph.graph.getNodes()) {
                if (letControlNodes.contains(node.getClass())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns a {@code Node} in this AbstractProgram's {@link #controlFlowGraph} which does not currently have an
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
