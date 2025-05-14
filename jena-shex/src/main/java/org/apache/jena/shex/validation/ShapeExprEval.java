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
import org.apache.jena.shex.reporting.NodeSatExprReport;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.calc.TypedNodeConstraintComponentVisitor;
import org.apache.jena.shex.calc.TypedShapeExprVisitor;
import org.apache.jena.shex.calc.Util;
import org.apache.jena.shex.expressions.*;
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
                                     ValidationContext2 vCxt, NodeSatExprReport report) {
        return satisfies(dataNode, expr, null, vCxt, report);
    }

    private static NodeSatExprReport nodeSatisfiesRefExact (Node dataNode, ShapeExprRef ref,
                                                      ValidationContext2 vCxt, NodeSatExprReport reportFactory) {
        return vCxt.validate(dataNode, ref, reportFactory);
    }

    private static boolean neighSatisfiesRefExact (Node commonFocus, Set<Triple> neigh, ShapeExprRef ref,
                                                   ValidationContext2 vCxt, NodeSatExprReport report) {
        ShapeExprEvalVisitor evaluator = new ShapeExprEvalVisitor(commonFocus, neigh, vCxt);
        return vCxt.getDefinition(ref.getLabel()).visit(evaluator, report);
    }





    private static boolean satisfies(Node dataNode, ShapeExpr expr, Set<Triple> neigh,
                                     ValidationContext2 vCxt, NodeSatExprReport report) {
        boolean result = false;
        if (expr instanceof ShapeExprRef ref) {
            for (Node base : vCxt.getNonAbstractSubtypes(ref.getLabel())) {
                if (neigh == null) {
                    NodeSatExprReport r = nodeSatisfiesRefExact(dataNode, ShapeExprRef.create(base), vCxt, report);
                    r.setParent(report);
                    if (ShexStatus.conformant == r.getStatus())
                        return true;
                } else {
                    if (neighSatisfiesRefExact(dataNode, neigh, ref, vCxt, report)) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            ShapeExprEvalVisitor evaluator = new ShapeExprEvalVisitor(dataNode, neigh, vCxt);
            return expr.visit(evaluator, report);
        }
    }

    /** Validates a node's neighbourhood or a set of triples against a shape.
     * If triples is null, the whole node's neighbourhood is considered. */
    private static boolean satisfiesShape(Shape shape, Node dataNode, Set<Triple> triples,
                                          ValidationContext2 vCxt, NodeSatExprReport report) {

        NodeSatExprReport childReport = report.create(dataNode, shape);
        childReport.setParent(report);

        // 1. Collect the shapes to be satisfied (several if the shape is with extends)
        //    and the corresponding constraints if the shape is with extends
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
        Set<Triple> accMatchables = new HashSet<>();
        Set<Triple> accNonMatchables = new HashSet<>();
        if (null == triples) {  // Validating the whole neighbourhood
            Util.retrieveRelevantNeighbourhood(vCxt.getGraph(), dataNode,
                    mainTripleExprs.values(),
                    accMatchables, accNonMatchables, vCxt);
        } else {   // Validating only part of the neighbourhood
            accMatchables = triples;
        }

        // 3. Check if the closed constraint is satisfied, if any
        if (shape.isClosed() && !accNonMatchables.isEmpty()) {
            report.addInfoFailure(shape, accMatchables, "CLOSED required but forbidden triples");
            return false;
        }

        // 4. Search for a split that satisfies the shape hierarchy and the extends constraints
        Iterator<Map<Node, Set<Triple>>> splitsIt = TripleExprEval.correctSplitsIterator(accMatchables, shape,
                mainTripleExprs, vCxt, childReport, shape, dataNode);

        while (splitsIt.hasNext()) {
            Map<Node, Set<Triple>> split = splitsIt.next();
            if (splitSatisfiesConstraints(split, constraints, vCxt, report, dataNode)) {
                report.setSatisfies(true);
                return true;
            }
        }

        report.setSatisfies(false);
        report.addInfoFailure(shape, triples, "Shape not satisfied by the triples.");
        return false;
    }

    /** Check whether a splitting of the triples between the supertypes also satisfies the constraints
     * of each supertype. */
    private static boolean splitSatisfiesConstraints (Map<Node, Set<Triple>> split,
                                                      Map<Node, List<ShapeExpr>> constraints,
                                                      ValidationContext2 vCxt,
                                                      NodeSatExprReport report,
                                                      Node nodeForReport) {
        Map<Node, Set<Triple>> relevantTriples = new HashMap<>();
        for (Map.Entry<Node, List<ShapeExpr>> e : constraints.entrySet()) {
            for (ShapeExpr constr : e.getValue()) {
                Set<Triple> triples = relevantTriples.putIfAbsent(e.getKey(),
                        vCxt.getSupertypes(e.getKey()).stream()
                            .flatMap(l -> split.get(l).stream())
                            .collect(Collectors.toSet()));
                if (!satisfies(nodeForReport, constr, triples, vCxt, report /* TODO which report is that? */)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean satisfies(NodeConstraint nodeConstraint, Node dataNode,
                                     NodeSatExprReport report) {
        NodeConstraintComponentEvalVisitor componentEval =
                new NodeConstraintComponentEvalVisitor(dataNode, report, nodeConstraint);
        return nodeConstraint.getComponents().stream().allMatch(ncc -> ncc.visit(componentEval));
    }


    // TODO report for all visit functions
    static class ShapeExprEvalVisitor implements TypedShapeExprVisitor<Boolean, NodeSatExprReport> {

        private final ValidationContext2 vCxt;
        private final Node dataNode;
        private final Set<Triple> triples;

        ShapeExprEvalVisitor(Node dataNode, Set<Triple> triples, ValidationContext2 vCxt) {
            this.vCxt = vCxt;
            this.dataNode = dataNode;
            this.triples = triples;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, NodeSatExprReport report) {
            for (ShapeExpr se : shapeAnd.getShapeExprs()) {
                if (! se.visit(this, report)) {
                    report.addInfoFailure(shapeAnd, triples, "AND not satisfied");
                    return false;
                }
            }
            return true;
        }

        @Override
        public Boolean visit(ShapeOr shapeOr, NodeSatExprReport report) {
            for (ShapeExpr se : shapeOr.getShapeExprs()) {
                if (se.visit(this, report))
                    return true;
            }
            report.addInfoFailure(shapeOr, triples, "None of the OR disjuncts was satisfied");
            return false;
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, NodeSatExprReport report) {
            if (shapeNot.getShapeExpr().visit(this, report)) {
                report.addInfoFailure(shapeNot, triples, "Negated expression is satisfied");
                return false;
            } else
                return true;
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, NodeSatExprReport report) {
            return satisfies(dataNode, shapeExprRef, triples, vCxt, report);
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, NodeSatExprReport report) {
            // TODO shape external never satisfied
            report.addInfoFailure(shapeExternal, triples, "Shape external not supported, never satisfied");
            return false;
        }

        @Override
        public Boolean visit(Shape shape, NodeSatExprReport report) {
            return satisfiesShape(shape, dataNode, triples, vCxt, report);
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, NodeSatExprReport report) {
            return satisfies(nodeConstraint, dataNode, report);
        }
    }

    static class NodeConstraintComponentEvalVisitor implements TypedNodeConstraintComponentVisitor<Boolean> {

        private final Node dataNode;
        private final NodeSatExprReport report;
        private final NodeConstraint parentConstraint;

        NodeConstraintComponentEvalVisitor(Node dataNode, NodeSatExprReport report, NodeConstraint parentConstraint) {
            this.dataNode = dataNode;
            this.report = report;
            this.parentConstraint = parentConstraint;
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
                report.addInfoFailure(nodeKindCstr,
                        nodeKindCstr + ": Expected " + nodeKind + " for " + displayStr(dataNode));
            return satisfied;
        }

        @Override
        public Boolean visit(DatatypeConstraint datatypeCstr) {
            if (!dataNode.isLiteral()) {
                report.addInfoFailure(datatypeCstr,
                        datatypeCstr + " : Not a literal");
                return false;
            }

            if (datatypeCstr.getDatatypeURI().equals(dataNode.getLiteralDatatypeURI())) {
                // Must be valid for the type
                if (!datatypeCstr.getRDFDatatype().isValid(dataNode.getLiteralLexicalForm())) {
                    report.addInfoFailure(datatypeCstr,
                            datatypeCstr + " : Not valid value : Node " + displayStr(dataNode));
                    return false;
                }
            } else {
                report.addInfoFailure(datatypeCstr,
                        datatypeCstr + " -- Wrong datatype: " + strDatatype(dataNode) + " for focus node: " + displayStr(dataNode));
                return false;
            }
            return true;
        }

        @Override
        public Boolean visit(NumLengthConstraint numLengthCstr) {
            if (!dataNode.isLiteral()) {
                report.addInfoFailure(numLengthCstr, format("NumericConstraint: Not numeric: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }

            RDFDatatype rdfDT = dataNode.getLiteralDatatype();
            if (!(rdfDT instanceof XSDDatatype)) {
                report.addInfoFailure(numLengthCstr,
                        format("NumericConstraint: Not a numeric: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }

            if (XSDDatatype.XSDfloat.equals(rdfDT) || XSDDatatype.XSDdouble.equals(rdfDT)) {
                report.addInfoFailure(numLengthCstr,
                        format("NumericConstraint: Numeric not compatible with xsd:decimal: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }
            String lexicalForm = dataNode.getLiteralLexicalForm();
            if (!rdfDT.isValid(lexicalForm)) {
                report.addInfoFailure(numLengthCstr,
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
            report.addInfoFailure(numLengthCstr, msg);
            return false;
        }

        @Override
        public Boolean visit(NumRangeConstraint numRangeCstr) {
            if (!dataNode.isLiteral()) {
                report.addInfoFailure(numRangeCstr, "NumRange: Not a literal number");
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
            report.addInfoFailure(numRangeCstr, msg);
            return false;
        }

        @Override
        public Boolean visit(StrRegexConstraint strRegexCstr) {
            if (dataNode.isBlank()) {
                String msg = toString() + ": Blank node: " + displayStr(dataNode);
                report.addInfoFailure(strRegexCstr, msg);
                return false;
            }
            String str = NodeFunctions.str(dataNode);
            if (strRegexCstr.getPattern().matcher(str).find()) {
                return true;
            }
            String msg = strRegexCstr + ": Does not match: '" + str + "'";
            report.addInfoFailure(strRegexCstr, msg);
            return false;
        }

        @Override
        public Boolean visit(StrLengthConstraint strLengthCstr) {
            StrLengthKind lengthType = strLengthCstr.getLengthType();
            int length = strLengthCstr.getLength();
            if (!dataNode.isLiteral() && !dataNode.isURI()) {
                String msg = format("%s: Not a literal or URI: %s", lengthType.label(), ShexLib.displayStr(dataNode));
                report.addInfoFailure(strLengthCstr, msg);
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
            report.addInfoFailure(strLengthCstr, msg);
            return false;
        }

        @Override
        public Boolean visit(ValueConstraint valueCstr) {
            boolean b = valueCstr.getValueSetRanges().stream()
                    .anyMatch(valueSetRange -> validateRange(valueSetRange, dataNode));
            if (!b) {
                report.addInfoFailure(valueCstr, "Value " + ShexLib.displayStr(dataNode) + " not in range: " + valueCstr);
                return false;
            }
            return true;
        }

        private boolean validateRange(ValueSetRange valueSetRange, Node data) {
            boolean b1 = valueSetRange.included(data);
            if (!b1)
                return false;
            boolean b2 = valueSetRange.excluded(data);
            if (b2)
                return false;
            // OK
            return true;
        }
    }


}
