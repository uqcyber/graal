package jdk.graal.compiler.core.test.veriopt;

import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.core.veriopt.VeriOptIsabelleUtil;
import jdk.graal.compiler.core.veriopt.VeriOptStampEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedConvertedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedOffsetInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedScaledInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import org.graalvm.collections.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class VeriOptProgramLoops {

    /**
     * Attempts to generate and return an Isabelle encoding of the loop and induction variable information for each
     * graph in the given {@code graphNameMapping}. <br>
     *
     * If there are no induction variables to encode, or there was an issue with translating the information, an empty
     * string is returned.
     *
     * @param graphNameMapping a mapping from graph names to graphs.
     * @return an Isabelle encoding of the loop and induction variable information for each graph in the mapping, or an
     *         empty string.
     * */
    public static String generateLoopInformation(HashMap<String, StructuredGraph> graphNameMapping) {
        try {
            return (new ProgramLoops(graphNameMapping)).generateLoopInformation().toString();
        } catch (RuntimeException exception) {
            System.err.println("Not encoding loop information as " + exception.getMessage());
            return "";
        }
    }

    /**
     * Represents an Isabelle encoding of the loops and induction variables for a program. <br>
     *
     * Contains all functionality necessary to translate the loop and induction variable information into an
     * Isabelle-readable syntax.
     * */
    public static final class ProgramLoops {

        /**
         * Defines the encoding segments for the Isabelle {@code ProgramLoops} definition.
         * */
        private enum EncodingSegment {
            DEFINITION_HEADER, GRAPH_HEADER, LOOP_HEADER, IV_HEADER,
            DEFINITION_FOOTER, GRAPH_FOOTER, LOOP_FOOTER, IV_FOOTER,
            IV_BASIC, DIV_OFFSET, DIV_SCALED, DIV_CONVERTED
        }

        // Header and footer of the Isabelle definition
        private static final String HEADER =
                "\n\ndefinition {name}_loops :: ProgramLoops where\n\t\"{name}_loops = Map.empty (\n";

        private static final String FOOTER = "\t)\"\n";

        // The ProgramLoops encoding
        private final StringBuilder encoding = new StringBuilder();

        // An encoding of a single graph's GraphLoops
        private StringBuilder graphEncoding = new StringBuilder();

        // Indicates whether induction variables were encoded for the current graph
        private boolean encoded = false;

        // Maps the name of each graph in the program to the graph itself
        private final HashMap<String, StructuredGraph> graphNameMapping;

        public ProgramLoops(HashMap<String, StructuredGraph> graphNameMapping) {
            this.graphNameMapping = graphNameMapping;
        }

        /**
         * Generates and stores the Isabelle {@code ProgramLoops} encoding for this program in {@link #encoding}, and
         * then returns this object. <br>
         *
         * The Isabelle {@code ProgramLoops} structure maps unique method signatures to {@code GraphLoops} definitions,
         * which encode all loops and induction variables in a graph. For the specific Isabelle syntax, see
         * {@link #addSegment}.
         *
         * @return this {@code ProgramLoops} object with its loops and induction variables encoded in {@link #encoding}.
         * */
        public ProgramLoops generateLoopInformation() {
            if (!VeriOpt.ENCODE_LOOPS) {
                // Not encoding loops, do nothing
                return this;
            }

            // Begin the definition
            addSegment(EncodingSegment.DEFINITION_HEADER);

            // Iterate through each graph, encoding loops if they exist
            for (String graphName : graphNameMapping.keySet()) {
                // Get the loop information for this graph
                LoopsData data = LoopsData.veriOptCompute(graphNameMapping.get(graphName));

                if (data.loops().isEmpty()) {
                    // No loops in this graph, no need to encode
                    continue;
                }

                // Encode the loops
                encodeGraphLoops(graphName, data);
                addGraphEncoding();
            }

            // Close the definition
            addSegment(EncodingSegment.DEFINITION_FOOTER);
            return finaliseEncoding();
        }

        /**
         * Encodes the loops in the graph defined by the given {@code graphName} into an Isabelle-readable syntax.
         * Functions as the beginning of an Isabelle {@code GraphLoops} definition.
         *
         * @param graphName the name of the graph whose loops are being encoded.
         * @param data the loop information for the graph defined by the given {@code graphName}.
         * */
        private void encodeGraphLoops(String graphName, LoopsData data) {
            // Begin the graph
            addSegment(EncodingSegment.GRAPH_HEADER, graphName);

            // Generate identifiers for all induction variables in this graph
            HashMap<InductionVariable, Pair<Integer, Integer>> ivIdentifiers = getInductionVariableIdentifiers(data);

            // Iterate through the loops and encode them as an Isabelle (Loop, GraalIVs) tuple
            for (int loopID = 0; loopID < data.loops().size(); loopID++) {
                Loop loop = data.loops().get(loopID);

                if (loop.getInductionVariables().isEmpty()) {
                    // No induction variables in this loop, no need to encode
                    continue;
                }

                // Encode this loop and its IVs
                encodeGraphLoop(loop, loopID, ivIdentifiers);
            }

            // Close the graph
            removeLastComma(graphEncoding, encoded);
            addSegment(EncodingSegment.GRAPH_FOOTER);
        }

        /**
         * Encodes a single {@code loop}, defined by the given {@code loop} and {@code loopID}, into an
         * Isabelle-readable syntax.
         *
         * @param loop the {@code loop} being encoded.
         * @param loopID the unique ID for this {@code loop}.
         * @param ivIdentifiers a mapping from all the induction variables (in the graph to which the given {@code loop}
         *                      belongs) to a tuple of their {@code loopID} and {@code ivID}.
         * */
        private void encodeGraphLoop(Loop loop, int loopID,
                                     HashMap<InductionVariable, Pair<Integer, Integer>> ivIdentifiers) {
            // Begin the loop
            addSegment(EncodingSegment.LOOP_HEADER, String.valueOf(loopID),
                    VeriOptIsabelleUtil.asNodeID(loop.loopBegin()));

            // Iterate through this loops induction variables and encode them
            for (InductionVariable iv : loop.getInductionVariables().getValues()) {
                encodeInductionVariable(iv, ivIdentifiers);
            }

            // Close the loop
            removeLastComma(graphEncoding, encoded);
            addSegment(EncodingSegment.LOOP_FOOTER);
        }

        /**
         * Encodes a single induction variable, defined by the given {@code iv}, into an Isabelle-readable syntax.
         *
         * @param iv the induction variable being encoded.
         * @param ivIdentifiers a mapping from all the induction variables (in the graph to which the given {@code iv}
         *                      belongs) to a tuple of their {@code loopID} and {@code ivID}.
         * */
        private void encodeInductionVariable(InductionVariable iv,
                                             HashMap<InductionVariable, Pair<Integer, Integer>> ivIdentifiers) {
            // Begin the induction variable
            addSegment(EncodingSegment.IV_HEADER);

            // Encode the induction variable & indicate that it has been encoded
            addSegment(getIVEncodingType(iv), getIVDefinitionParameters(iv, ivIdentifiers).toArray(new String[0]));
            encoded = true;

            // Close the induction variable
            addSegment(EncodingSegment.IV_FOOTER);
        }

        /**
         * Generates and returns a list of parameters to use when constructing the induction variable encoding segment
         * for the given {@code iv}. The amount and types of parameters that each induction variable type expects is
         * defined by the Isabelle {@code GraalIV} datatype. <br>
         *
         * <pre>
         * datatype GraalIV =
         *     BasicIV            (id: identifier) (phi: ID)  (init: ID) (stride: ID)   (op: IRBinaryOp)
         *   | DerivedOffsetIV    (id: identifier) (base_iv: identifier) (offset: ID)   (val: IRBinaryOp)
         *   | DerivedScaledIV    (id: identifier) (base_iv: identifier) (scale: ID)    (val': ID)
         *   | DerivedConvertedIV (id: identifier) (base_iv: identifier) (stamp: Stamp) (val': ID)
         * </pre>
         *
         * Where {@code ID} is an integer representing a node's ID in a graph, {@code identifier} is a tuple of
         * integers {@code (loopID, ivID)} uniquely identifying an induction variable, and {@code IRBinaryOp} has
         * several possible definitions, with the supported constructors defined in
         * {@link VeriOptIsabelleUtil#IRBinaryOps}.
         *
         * @param iv the induction variable whose encoding segment parameters are being gathered.
         * @param ivIdentifiers a mapping from all the induction variables (in the graph to which the given {@code iv}
         *                      belongs) to a tuple of their {@code loopID} and {@code ivID}.
         * @return a list of parameters to use in the encoding segment of the given {@code iv}.
         * */
        private ArrayList<String> getIVDefinitionParameters(
                InductionVariable iv, HashMap<InductionVariable, Pair<Integer, Integer>> ivIdentifiers) {
            // Get the induction variable's ivID and loopID
            Integer loopID = ivIdentifiers.get(iv).getLeft();
            Integer ivID = ivIdentifiers.get(iv).getRight();

            // Add the identifier (loopID, ivID) to the parameters
            ArrayList<String> parameters = new ArrayList<>(Arrays.asList(String.valueOf(loopID), String.valueOf(ivID)));

            // Gather the parameters based on the induction variable type
            if (iv instanceof BasicInductionVariable basicIV) {
                // Basic induction variable
                parameters.addAll(Arrays.asList(VeriOptIsabelleUtil.asNodeID(basicIV.valueNode()),
                        VeriOptIsabelleUtil.asNodeID(basicIV.initNode()),
                        VeriOptIsabelleUtil.asNodeID(basicIV.strideNode()),
                        VeriOptIsabelleUtil.asIRBinaryOp(basicIV.getOp())));
            } else {
                // Derived induction variable
                DerivedInductionVariable derivedIV = (DerivedInductionVariable) iv;

                // Get the base induction variable's identifier and add it to the parameters
                Integer baseLoop = ivIdentifiers.get(derivedIV.getBase()).getLeft();
                Integer baseID = ivIdentifiers.get(derivedIV.getBase()).getRight();
                parameters.addAll(Arrays.asList(String.valueOf(baseLoop), String.valueOf(baseID)));

                // Get the remainder of the parameters based on the derived induction variable type
                if (iv instanceof DerivedOffsetInductionVariable offset) {
                    parameters.addAll(Arrays.asList(VeriOptIsabelleUtil.asNodeID(offset.getOffset()),
                            VeriOptIsabelleUtil.asIRBinaryOp(offset.valueNode())));
                } else if (iv instanceof DerivedScaledInductionVariable scaled) {
                    parameters.addAll(Arrays.asList(VeriOptIsabelleUtil.asNodeID(scaled.getScale()),
                            VeriOptIsabelleUtil.asNodeID(scaled.valueNode())));
                } else if (iv instanceof DerivedConvertedInductionVariable converted) {
                    parameters.addAll(Arrays.asList(VeriOptStampEncoder.encodeStamp(converted.veriOptStamp()),
                            VeriOptIsabelleUtil.asNodeID(converted.valueNode())));
                }
            }

            // Return the parameters to use in the encoding segment
            return parameters;
        }

        /**
         * Returns the {@link EncodingSegment} type for the provided induction variable ({@code iv})'s type. One of
         * {@code IV_BASIC}, {@code DIV_OFFSET}, {@code DIV_SCALED} or {@code DIV_CONVERTED}.
         *
         * @param iv the induction variable whose segment type is being determined.
         * @return the {@code EncodingSegment} type for the provided induction variable ({@code iv}).
         * */
        private EncodingSegment getIVEncodingType(InductionVariable iv) {
            return (iv instanceof BasicInductionVariable)         ? EncodingSegment.IV_BASIC   :
                   (iv instanceof DerivedOffsetInductionVariable) ? EncodingSegment.DIV_OFFSET :
                   (iv instanceof DerivedScaledInductionVariable) ? EncodingSegment.DIV_SCALED :
                                                                    EncodingSegment.DIV_CONVERTED;
        }

        /**
         * Generates and returns a mapping from induction variables to a unique identifier consisting of their
         * {@code loopID} and {@code ivID}. This is performed for all induction variables in the graph whose loop
         * {@code data} information is provided.
         *
         * @param data information about all loops in the graph.
         * @return a mapping from all the induction variables (in the graph whose loops {@code data} is provided) to a
         *         tuple of their {@code loopID} and {@code ivID}.
         * */
        private HashMap<InductionVariable, Pair<Integer, Integer>> getInductionVariableIdentifiers(LoopsData data) {
            HashMap<InductionVariable, Pair<Integer, Integer>> ivIdentifiers = new HashMap<>();

            // Iterate through the loops, and store any induction variables as (IV => (LoopID, ivID))
            for (int loopID = 0; loopID < data.loops().size(); loopID++) {
                Loop loop = data.loops().get(loopID);

                if (loop.getInductionVariables().isEmpty()) {
                    // No induction variables for this loop
                    continue;
                }

                int ivID = 0;
                for (InductionVariable iv : loop.getInductionVariables().getValues()) {
                    // Populate the mapping with the loop & induction variable identifier
                    ivIdentifiers.put(iv, Pair.create(loopID, ivID++));
                }
            }

            // Return the populated mapping
            return ivIdentifiers;
        }

        /**
         * Extends the {@code ProgramLoops} encoding (either {@link #encoding} or {@link #graphEncoding}) with a
         * {@code segment} of the given type, and applies the given {@code arguments} to the segment definition. <br>
         *
         * In most cases, the {@link #graphEncoding} is extended. The {@link #encoding} is extended only when the
         * {@link #HEADER} or {@link #FOOTER} are being added.
         *
         * @param segment the {@code segment} type being added to the encoding.
         * @param arguments the (possibly empty) {@code arguments} being applied to the {@code segment} definition.
         * @throws RuntimeException if the {@code segment} definition expected more {@code arguments} than provided.
         * */
        private void addSegment(EncodingSegment segment, String... arguments) {
            // Attain the Isabelle syntax for the encoding segment
            String addition = switch (segment) {
                case DEFINITION_HEADER -> HEADER;
                case DEFINITION_FOOTER -> FOOTER;

                case GRAPH_HEADER -> "\t''%s'' \\<mapsto> [\n";
                case GRAPH_FOOTER -> "\t],\n";

                case LOOP_HEADER -> "\t\t((NewLoop %s %s), [\n";
                case LOOP_FOOTER -> "\t\t]),\n";

                case IV_HEADER -> "\t\t  ";
                case IV_FOOTER -> ",\n";

                case IV_BASIC -> "BasicIV (%s, %s) %s %s %s %s";
                case DIV_OFFSET -> "DerivedOffsetIV (%s, %s) (%s, %s) %s %s";
                case DIV_SCALED -> "DerivedScaledIV (%s, %s) (%s, %s) %s %s";
                case DIV_CONVERTED -> "DerivedConvertedIV (%s, %s) (%s, %s) (%s) %s";
            };

            // Insert any provided arguments
            for (String argument : arguments) {
                addition = addition.replaceFirst("%s", argument);
            }

            // Ensure that sufficient arguments were provided
            if (addition.contains("%s")) {
                throw new RuntimeException(String.format("insufficient arguments provided for segment definition (%s).",
                        addition));
            }

            // Append this segment to the particular encoding
            if (segment == EncodingSegment.DEFINITION_FOOTER || segment == EncodingSegment.DEFINITION_HEADER) {
                encoding.append(addition);
            } else {
                graphEncoding.append(addition);
            }
        }

        /**
         * Adds the most recent {@code GraphLoops} encoding ({@link #graphEncoding}) into the outer
         * {@code ProgramLoops} definition ({@link #encoding}), if any induction variables were encoded for the
         * most recent graph, and prepares for the next {@code GraphLoops} encoding.
         * */
        private void addGraphEncoding() {
            // Extend the outer ProgramLoops encoding with the current GraphLoops
            if (encoded) {
                encoding.append(graphEncoding);
            }

            // Reset the GraphLoops encoding
            graphEncoding = new StringBuilder();
            encoded = false;
        }

        /**
         * Finalises the Isabelle {@code ProgramLoops} {@link #encoding} and returns this {@code ProgramLoops}. <br>
         *
         * If no induction variables were encoded, the {@link #encoding} is cleared. If there were, a trailing comma is
         * removed.
         *
         * @return this ProgramLoops with the {@link #encoding} finalised.
         * */
        private ProgramLoops finaliseEncoding() {
            if (encodingEmpty()) {
                // Nothing to encode
                encoding.setLength(0);
            } else {
                // Remove trailing comma
                removeLastComma(encoding, true);
            }

            return this;
        }

        /**
         * Removes the last comma {@code (,)} in the given {@code encoding} if the provided {@code condition} is met.
         *
         * @param encoding the {@code encoding} whose last comma is being removed.
         * @param condition the {@code condition} which must be met for the last comma to be removed.
         * */
        private void removeLastComma(StringBuilder encoding, boolean condition) {
            if (condition) {
                VeriOptIsabelleUtil.StringFormatting.removeLastInstanceOfSymbol(encoding, ",");
            }
        }

        /**
         * Returns whether the {@code ProgramLoops} {@link #encoding} is empty, i.e., no loops or induction variables
         * were encoded.
         *
         * @return {@code true} if the {@code ProgramLoops} did not encode any loops or induction variables, else
         *         {@code false}.
         * */
        private boolean encodingEmpty() {
            return toString().equals(HEADER + FOOTER);
        }

        @Override
        public String toString() {
            return encoding.toString();
        }
    }

}
