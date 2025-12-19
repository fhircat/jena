/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.shex.validation;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.out.NodeFmtLib;
import org.apache.jena.shex.calc.TypedNodeConstraintComponentVisitor;
import org.apache.jena.shex.calc.TypedShapeExprVisitor;
import org.apache.jena.shex.calc.Util;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.reporting.Reporter;
import org.apache.jena.shex.sys.ShexLib;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.NodeFunctions;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.jena.shex.sys.ShexLib.displayStr;
import static org.apache.jena.shex.sys.ShexLib.strDatatype;

public class ShapeExprEval {

    public static boolean satisfies (Node dataNode, ShapeExpr expr,
                                     ValidationContext vCxt, Reporter reporter) {
        // Does not notify the reporter about conformant / non-conformant
        return _satisfies(dataNode, expr, null, vCxt, reporter)
            && vCxt.dispatchShapeExprSemanticAction(dataNode, expr, reporter);
    }

    private static boolean _satisfies(Node dataNode, ShapeExpr expr, Set<Triple> neigh,
                                      ValidationContext vCxt, Reporter reporter) {
        // Notifies the reporter about conformant / non-conformant.
        if (expr instanceof ShapeExprRef ref) {
            if (neigh == null)
                return vCxt.validate(dataNode, ref, reporter);
            else {
                List<Node> nonAbstractDescendants = vCxt.nonAbstractDescendants(ref.getLabel());

                boolean isDescendant = nonAbstractDescendants.get(0) != ref.getLabel(); /// <errorReporting/>

                for (Node descendant : nonAbstractDescendants) {
                    // TODO why not recurse on the reference here ? -> there is a reason I do not recall
                    ShapeExpr defn = vCxt.getDefinition(descendant);
                    Reporter defnReporter = reporter.createChild(dataNode, defn, neigh);

                    /// <errorReporting> the first element of nonAbstractDescendants is not a proper descendant
                    if (! isDescendant) isDescendant = true;  //
                    else defnReporter.informValidatingDescendant(descendant);
                    /// </errorReporting>

                    // TODO possible problem if the the definition is a reference itself
                    if (satisfiesNonRefExpr(dataNode, defn, neigh, vCxt, defnReporter)) {
                        reporter.informDescendantConformance(true, descendant);
                        return true;
                    }
                }
                if (nonAbstractDescendants.size() > 1 || vCxt.getShapeDecl(ref.getLabel()).isAbstract()) {
                    reporter.informDescendantConformance(false, null);
                }
                return false;
            }
        } else {
            return satisfiesNonRefExpr(dataNode, expr, neigh, vCxt, reporter);
        }
    }

    private static boolean satisfiesNonRefExpr (Node node, ShapeExpr expr, Set<Triple> neigh,
                                                ValidationContext vCxt, Reporter reporter) {
        // Does not directly notify the reporter about conformant / non-conformant. Should be done by the visit method
        ShapeExprEvalVisitor evaluator = new ShapeExprEvalVisitor(node, neigh, vCxt);
        return expr.visit(evaluator, reporter);
    }

    /** Validates a node's neighbourhood or a set of triples against a shape.
     * If triples is null, the whole node's neighbourhood is considered. */
    private static boolean satisfiesShape(Node dataNode,
                                          Shape shape,
                                          Set<Triple> triples,
                                          ValidationContext vCxt,
                                          Reporter shapeReporter) {
        // Notifies the reporter about conformant / non-conformant.

        // 1. Collect the shapes to be satisfied, distinguishing the main triple expressions and the constraints
        //    The shape's triple expression and constraint are added to the maps with key null
        //    If the shape is not in an extension hierarchy, then these maps contain only one entry with null key
        Map<Node, TripleExpr> mainTripleExprs = new HashMap<>();
        Map<Node, List<ShapeExpr>> constraints = new HashMap<>();
        mainTripleExprs.put(null, shape.getTripleExpr());
        for (ShapeExprRef ref: shape.getExtends()) {
            for (Node superType: vCxt.getSupertypes(ref.getLabel())) {
                Pair<Shape, List<ShapeExpr>> mc = Util.mainShapeAndConstraints(vCxt.getDefinition(superType), vCxt::getDefinition);
                mainTripleExprs.put(superType, mc.getLeft().getTripleExpr());
                constraints.put(superType, mc.getRight());
            }
        }

        // 2. Extract the neighbourhood of the node relevant for satisfying that shape
        Set<Triple> matchables = new HashSet<>();
        Set<Triple> nonMatchables = new HashSet<>();
        if (null == triples) {  // Validating the whole neighbourhood
            Util.retrieveRelevantNeighbourhood(vCxt.getGraph(), dataNode,
                    mainTripleExprs.values(), matchables, nonMatchables, vCxt);
        } else {   // Validating only part of the neighbourhood
            matchables = triples;
        }

        // 3. Check if the closed constraint is satisfied, if any
        if (shape.isClosed() && !nonMatchables.isEmpty()) {
            shapeReporter.informUnexpectedTriples(nonMatchables, Reporter.UnexpectedTriplesReason.CLOSED);
            return shapeReporter.setIsConformant(false);
        }

        // 4. Iterate over splits that satisfy the main triple expressions of the hierarchy, and look for a split
        //    that satisfies the constraints.
        Iterator<Map<Node, Set<Triple>>> splitsIt = TripleExprEval.correctSplitsIterator(dataNode, shape, matchables,
                mainTripleExprs, vCxt, shapeReporter);
        // TODO specific reporting needed when a split satisfies the main shapes but not the constraints
        //      Need a test case for that.
        //      It should have several equivalent shapes, but in which the ordering is different
        while (splitsIt.hasNext()) {
            Map<Node, Set<Triple>> split = splitsIt.next();
            if (splitSatisfiesConstraints(dataNode, shape, split, constraints, vCxt, shapeReporter)) {
                return shapeReporter.setIsConformant(true);
            }
        }
        return shapeReporter.setIsConformant(false);
    }

    /** Check whether a splitting of the triples between the supertypes also satisfies the constraints
     * of each supertype. */
    private static boolean splitSatisfiesConstraints (Node dataNode,
                                                      Shape shape,
                                                      Map<Node, Set<Triple>> split,
                                                      Map<Node, List<ShapeExpr>> constraints,
                                                      ValidationContext vCxt,
                                                      Reporter shapeReporter) {
        // Does not directly notify the reporter about conformant / non-conformant.
        Map<Node, Set<Triple>> relevantTriples = new HashMap<>();
        for (Map.Entry<Node, List<ShapeExpr>> e : constraints.entrySet()) {
            for (ShapeExpr constr : e.getValue()) {
                Set<Triple> triples = relevantTriples.putIfAbsent(e.getKey(),
                        vCxt.getSupertypes(e.getKey()).stream()
                            .flatMap(l -> split.get(l).stream())
                            .collect(Collectors.toSet()));
                Reporter subReporter = shapeReporter.createChild(dataNode, constr, triples);
                if (!_satisfies(dataNode, constr, triples, vCxt, subReporter)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean satisfiesNodeConstraint(NodeConstraint nodeConstraint,
                                                   Node dataNode,
                                                   Reporter reporter) {
        // Notifies the reporter about conformant / non-conformant
        NodeConstraintComponentEvalVisitor componentEval =
                new NodeConstraintComponentEvalVisitor(dataNode, reporter);
        return reporter.setIsConformant(nodeConstraint.getComponents().stream()
                .allMatch(ncc -> ncc.visit(componentEval)));
    }

    private static class ShapeExprEvalVisitor implements TypedShapeExprVisitor<Boolean, Reporter> {

        private final ValidationContext vCxt;
        private final Node dataNode;
        private final Set<Triple> triples;

        ShapeExprEvalVisitor(Node dataNode, Set<Triple> triples, ValidationContext vCxt) {
            this.vCxt = vCxt;
            this.dataNode = dataNode;
            this.triples = triples;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, Reporter reporter) {
            for (ShapeExpr se : shapeAnd.getShapeExprs()) {
                if (! se.visit(this, reporter.createChild(dataNode, se, triples))) {
                    return reporter.setIsConformant(false);
                }
            }
            return reporter.setIsConformant(true);
        }

        @Override
        public Boolean visit(ShapeOr shapeOr, Reporter reporter) {
            for (ShapeExpr se : shapeOr.getShapeExprs()) {
                if (se.visit(this, reporter.createChild(dataNode, se, triples))) {
                    return reporter.setIsConformant(true);
                }
            }
            return reporter.setIsConformant(false);
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, Reporter reporter) {
            ShapeExpr subExpr = shapeNot.getShapeExpr();
            boolean subExprIsValid = subExpr.visit(this, reporter.createChild(dataNode, subExpr, triples));
            return reporter.setIsConformant(! subExprIsValid);
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, Reporter reporter) {
            return _satisfies(dataNode, shapeExprRef, triples, vCxt, reporter);
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, Reporter reporter) {
            // TODO shape external never satisfied
            reporter.informExternalNotSupported();
            return reporter.setIsConformant(false);
        }

        @Override
        public Boolean visit(Shape shape, Reporter reporter) {
            return satisfiesShape(dataNode, shape, triples, vCxt, reporter);
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, Reporter reporter) {
            return satisfiesNodeConstraint(nodeConstraint, dataNode, reporter);
        }
    }

    private static class NodeConstraintComponentEvalVisitor implements TypedNodeConstraintComponentVisitor<Boolean> {

        private final Node dataNode;
        private final Reporter reporter;

        NodeConstraintComponentEvalVisitor(Node dataNode, Reporter reporter) {
            this.dataNode = dataNode;
            this.reporter = reporter;
        }

        @Override
        public Boolean visit(NodeKindConstraint nodeKindCstr) {
            NodeKind nodeKind = nodeKindCstr.getNodeKind();
            boolean satisfied = switch (nodeKind) {
                case BNODE -> dataNode.isBlank();
                case IRI -> dataNode.isURI();
                case LITERAL -> dataNode.isLiteral();
                case NONLITERAL -> !dataNode.isLiteral();
                default -> true;
            };

            // TODO Bad.
            if (!satisfied)
                reporter.informInvalidNodeConstraintComponent(nodeKindCstr,
                        nodeKindCstr + ": Expected " + nodeKind + " for " + displayStr(dataNode));
            return satisfied;
        }

        @Override
        public Boolean visit(DatatypeConstraint datatypeCstr) {
            if (!dataNode.isLiteral()) {
                reporter.informInvalidNodeConstraintComponent(datatypeCstr,
                        datatypeCstr + " : Not a literal");
                return false;
            }

            if (datatypeCstr.getDatatypeURI().equals(dataNode.getLiteralDatatypeURI())) {
                // Must be valid for the type
                if (!datatypeCstr.getRDFDatatype().isValid(dataNode.getLiteralLexicalForm())) {
                    reporter.informInvalidNodeConstraintComponent(datatypeCstr,
                            datatypeCstr + " : Not valid value : Node " + displayStr(dataNode));
                    return false;
                }
            } else {
                reporter.informInvalidNodeConstraintComponent(datatypeCstr,
                        datatypeCstr + " -- Wrong datatype: " + strDatatype(dataNode) + " for focus node: " + displayStr(dataNode));
                return false;
            }
            return true;
        }

        @Override
        public Boolean visit(NumLengthConstraint numLengthCstr) {
            if (!dataNode.isLiteral()) {
                reporter.informInvalidNodeConstraintComponent(numLengthCstr, format("NumericConstraint: Not numeric: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }

            RDFDatatype rdfDT = dataNode.getLiteralDatatype();
            if (!(rdfDT instanceof XSDDatatype)) {
                reporter.informInvalidNodeConstraintComponent(numLengthCstr,
                        format("NumericConstraint: Not a numeric: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }

            if (XSDDatatype.XSDfloat.equals(rdfDT) || XSDDatatype.XSDdouble.equals(rdfDT)) {
                reporter.informInvalidNodeConstraintComponent(numLengthCstr,
                        format("NumericConstraint: Numeric not compatible with xsd:decimal: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }
            String lexicalForm = dataNode.getLiteralLexicalForm();
            if (!rdfDT.isValid(lexicalForm)) {
                reporter.informInvalidNodeConstraintComponent(numLengthCstr,
                        format("NumericConstraint: Not a valid xsd:decimal: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }

            int N = lexicalForm.length();
            int idx = lexicalForm.indexOf('.');

            switch (numLengthCstr.getLengthType()) {
                case FRACTIONDIGITS: {
                    // Does not include trailing zeros.
                    if (idx < 0) {
                        return true;
                    }
                    //int before = idx;
                    int after = lexicalForm.length() - idx - 1;
                    for (int i = N - 1; i > idx; i--) {
                        if (lexicalForm.charAt(i) != '0')
                            break;
                        after--;
                    }
                    if (after <= numLengthCstr.getLength()) {
                        return true;
                    }
                    break;
                }
                case TOTALDIGITS: {
                    // Canonical form.
                    int start = 0;
                    char ch1 = lexicalForm.charAt(0);
                    if (ch1 == '+' || ch1 == '-')
                        start++;
                    // Leading zeros
                    for (int i = start; i < N; i++) {
                        if (lexicalForm.charAt(i) != '0')
                            break;
                        start++;
                    }
                    int finish = N;
                    // Trailing zeros
                    if (idx >= 0) {
                        finish--;
                        for (int i = N - 1; i > idx; i--) {
                            if (lexicalForm.charAt(i) != '0')
                                break;
                            finish--;
                        }
                    }
                    int digits = finish - start;

                    if (digits <= numLengthCstr.getLength()) {
                        return true;
                    }
                    break;
                }
                default:
                    break;
            }

            String msg = format("Expected %s %d : got = %d", numLengthCstr.getLengthType().label(),
                    numLengthCstr.getLength(), lexicalForm.length());
            reporter.informInvalidNodeConstraintComponent(numLengthCstr, msg);
            return false;
        }

        @Override
        public Boolean visit(NumRangeConstraint numRangeCstr) {
            if (!dataNode.isLiteral()) {
                reporter.informInvalidNodeConstraintComponent(numRangeCstr, "NumRange: Not a literal number");
                return false;
            }
            NodeValue nv = NodeValue.makeNode(dataNode);
            int r = NodeValue.compare(nv, numRangeCstr.getNumericValue());

            switch (numRangeCstr.getRangeKind()) {

                case MAXEXCLUSIVE:
                    if (r < 0) {
                        return true;
                    }
                    break;
                case MAXINCLUSIVE:
                    if (r <= 0) {
                        return true;
                    }
                    break;
                case MINEXCLUSIVE:
                    if (r > 0) {
                        return true;
                    }
                    break;
                case MININCLUSIVE:
                    if (r >= 0) {
                        return true;
                    }
                    break;
            }
            String msg = format("Expected %s %s : got = %s", numRangeCstr.getRangeKind().label(), NodeFmtLib.strTTL(nv.getNode()), NodeFmtLib.strTTL(dataNode));
            reporter.informInvalidNodeConstraintComponent(numRangeCstr, msg);
            return false;
        }

        @Override
        public Boolean visit(StrRegexConstraint strRegexCstr) {
            if (dataNode.isBlank()) {
                String msg = toString() + ": Blank node: " + displayStr(dataNode);
                reporter.informInvalidNodeConstraintComponent(strRegexCstr, msg);
                return false;
            }
            String str = NodeFunctions.str(dataNode);
            if (strRegexCstr.getPattern().matcher(str).find()) {
                return true;
            }
            String msg = strRegexCstr + ": Does not match: '" + str + "'";
            reporter.informInvalidNodeConstraintComponent(strRegexCstr, msg);
            return false;
        }

        @Override
        public Boolean visit(StrLengthConstraint strLengthCstr) {
            StrLengthKind lengthType = strLengthCstr.getLengthType();
            int length = strLengthCstr.getLength();
            if (!dataNode.isLiteral() && !dataNode.isURI()) {
                String msg = format("%s: Not a literal or URI: %s", lengthType.label(), ShexLib.displayStr(dataNode));
                reporter.informInvalidNodeConstraintComponent(strLengthCstr, msg);
                return false;
            }
            String str = NodeFunctions.str(dataNode);
            switch (lengthType) {
                case LENGTH:
                    if (str.length() == length) {
                        return true;
                    }
                    break;
                case MAXLENGTH:
                    if (str.length() <= length) {
                        return true;
                    }
                    break;
                case MINLENGTH:
                    if (str.length() >= length) {
                        return true;
                    }
                    break;
            }

            String msg = format("Expected %s %d : got = %d", lengthType.label(), length, str.length());
            reporter.informInvalidNodeConstraintComponent(strLengthCstr, msg);
            return false;
        }

        @Override
        public Boolean visit(ValueConstraint valueCstr) {
            boolean b = valueCstr.getValueSetRanges().stream()
                    .anyMatch(valueSetRange -> validateRange(valueSetRange, dataNode));
            if (!b) {
                reporter.informInvalidNodeConstraintComponent(valueCstr, "Value " + ShexLib.displayStr(dataNode) + " not in range: " + valueCstr);
                return false;
            }
            return true;
        }

        private boolean validateRange(ValueSetRange valueSetRange, Node data) {
            boolean b1 = valueSetRange.included(data);
            if (!b1)
                return false;
            return ! valueSetRange.excluded(data);
            // OK
        }
    }


}
