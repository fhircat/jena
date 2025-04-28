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

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.out.NodeFmtLib;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.calc.TypedNodeConstraintComponentVisitor;
import org.apache.jena.shex.calc.TypedShapeExprVisitor;
import org.apache.jena.shex.calc.Util;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.sys.ShexLib;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.NodeFunctions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.jena.shex.sys.ShexLib.displayStr;
import static org.apache.jena.shex.sys.ShexLib.strDatatype;

public class ShapeExprEval {

    public static void satisfies (ShapeDecl shapeDecl, Node dataNode,
                                         ValidationContext vCxt, AShexReport report) {
        report.setSatisfies(_satisfies(shapeDecl, dataNode, vCxt, report, false));
    }

    private static boolean _satisfies(ShapeDecl shapeDecl, Node dataNode, ValidationContext vCxt,
                                      AShexReport report, boolean createChildReport) {
        if (createChildReport)
            report = report.createChild(dataNode, null, shapeDecl);
        boolean result = false;
        for (ShapeDecl base : vCxt.getTypeHierarchyGraph().getNonAbstractSubtypes(shapeDecl)) {
            vCxt.startValidate(base, dataNode);
            try {
                ShapeExpr shapeExpr = base.getShapeExpr();
                if (Util.hasExtends(shapeExpr, vCxt::getShapeDecl)) {
                    // TODO semantic actions and extends
                    result = satisfiesWithExtends(base, dataNode, vCxt, report);
                } else {
                    // TODO report for semantic actions
                    result = satisfies(shapeExpr, dataNode, vCxt, report)
                            && vCxt.dispatchShapeExprSemanticAction(shapeExpr, dataNode);
                }
            } finally { // TODO What exception could we have here ?
                vCxt.finishValidate(base, dataNode);
            }
        }
        report.setSatisfies(result);
        return result;
    }

    /*package*/
    static boolean satisfies(ShapeExpr shapeExpr, Node node, ValidationContext vCxt,
                             AShexReport report) {

        ShapeExprEvalVisitor evaluator = new ShapeExprEvalVisitor(node, vCxt);
        return shapeExpr.visit(evaluator, report);
    }

    private static boolean satisfiesWithExtends(ShapeDecl shapeDeclWithExtends, Node dataNode,
                                                ValidationContext vCxt, AShexReport parentReport) {

        // maps extended labels to their respective main shapes
        Map<Node, Shape> baseMainShapes = vCxt.getTypeHierarchyGraph().getSupertypes(shapeDeclWithExtends).stream()
                .collect(Collectors.toMap(ShapeDecl::getLabel,
                        sd -> Util.mainShape(sd.getShapeExpr(), vCxt::getShapeDecl)
                ));

        Set<Triple> accMatchables = new HashSet<>();
        Set<Triple> accNonMatchables = new HashSet<>();
        Util.retrieveRelevantNeighbourhood(vCxt.getGraph(), dataNode,
                baseMainShapes.values().stream().map(Shape::getTripleExpr).collect(Collectors.toList()),
                accMatchables, accNonMatchables, vCxt);

        Shape mainShape = baseMainShapes.get(shapeDeclWithExtends.getLabel());
        AShexReport mainShapeReport = parentReport.createChild(dataNode, mainShape, null);
        if (mainShape.isClosed() && !accNonMatchables.isEmpty()) {
            mainShapeReport.addInfoFailure(mainShape, dataNode, accMatchables,
                    "CLOSED required but forbidden triples");
            return false;
        }

        // TODO iteration here (see below)
        Map<Node, Set<Triple>> satisfyingTriples = TripleExprEval.matchesShapeWithExtends(
                accMatchables, mainShape,
                baseMainShapes, vCxt, mainShapeReport, dataNode);

        // TODO here, the triple expr part of the ext. hierarchy is checked. For better error reportig,
        //      we should explore the reason of the failure with non sorbe validation
        if (satisfyingTriples == null) {
            mainShapeReport.addInfoFailure(mainShape, dataNode, accMatchables,
                    "The neighbourhood didn't match the triple expressions of the extension hierarchy");
            return false;
        }

        // TODO: potential bug here
        //       it may be the case that a first splitting (found above) did satisfy the
        //       triple expressions, but does not allow to satisfy the constraints
        //       but another splitting allows to satisfy the constraints
        //       So, we would need an iterator over all the possible ways of satisfying the
        //       triple expressions, until none remains
        // TODO: a test case for that potential bug
        // TODO: here, the constraints of the current ShapeDecl are treated as constraints of a shape with extends
        //       In semantics defined in ESWC, they are treated as usual AND. Should we treat them apart here?
        //       In particular, are the errors part of the main shape report, or of the general report ?
        for (Node label : baseMainShapes.keySet()) {
            ShapeDecl shapeDecl = vCxt.getShapeDecl(label);
            for (ShapeExpr constr : Util.constraints(shapeDecl.getShapeExpr(), vCxt::getShapeDecl)) {
                Set<Triple> triples = vCxt.getTypeHierarchyGraph().getSupertypes(shapeDecl).stream()
                        .map(ShapeDecl::getLabel)
                        .flatMap(l -> satisfyingTriples.get(l).stream())
                        .collect(Collectors.toSet());
                if (!satisfiesExtendsConstraint(constr, dataNode, triples, vCxt, mainShapeReport)) {
                    mainShapeReport.addInfoFailure(constr, dataNode, triples,
                            "The part of the neighbourhood did not match the constraints");
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean satisfiesExtendsConstraint(ShapeExpr constr, Node node,
                                                      Set<Triple> neigh,
                                                      ValidationContext vCxt,
                                                      AShexReport report) {
        ExtendsConstraintEvalVisitor evaluator = new ExtendsConstraintEvalVisitor(node, vCxt, neigh);
        return constr.visit(evaluator, report);
    }

    // TODO parameter vCxt not used
    private static boolean satisfies(NodeConstraint nodeConstraint, Node dataNode,
                                     AShexReport report) {
        NodeConstraintComponentEvalVisitor componentEval =
                new NodeConstraintComponentEvalVisitor(dataNode, report, nodeConstraint);
        return nodeConstraint.getComponents().stream().allMatch(ncc -> ncc.visit(componentEval));
    }

    // TODO report for all visit functions
    static class ShapeExprEvalVisitor implements TypedShapeExprVisitor<Boolean, AShexReport> {

        private final ValidationContext vCxt;
        private final Node dataNode;

        ShapeExprEvalVisitor(Node data, ValidationContext vCxt) {
            this.vCxt = vCxt;
            this.dataNode = data;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, AShexReport report) {
            for (ShapeExpr se : shapeAnd.getShapeExprs()) {
                if (! se.visit(this, report)) {
                    report.addInfoFailure(shapeAnd, dataNode, null, "AND not satisfied");
                    return false;
                }
            }
            return true;
        }

        @Override
        public Boolean visit(ShapeOr shapeOr, AShexReport report) {
            for (ShapeExpr se : shapeOr.getShapeExprs()) {
                if (se.visit(this, report))
                    return true;
            }
            report.addInfoFailure(shapeOr, dataNode, null, "None of the OR disjuncts was satisfied");            return false;
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, AShexReport report) {
            if (shapeNot.getShapeExpr().visit(this, report)) {
                report.addInfoFailure(shapeNot, dataNode, null, "Negated expression is satisfied");
                return false;
            } else
                return true;
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, AShexReport report) {
            ShapeDecl shapeDecl = vCxt.getShapeDecl(shapeExprRef.getLabel());
            if (vCxt.cycle(dataNode, shapeDecl))
                return true;
            else if (_satisfies(shapeDecl, dataNode, vCxt, report, true))
                return true;
            else {
                // TODO report needed ?
                report.addInfoFailure(shapeExprRef, dataNode, null, "Shape reference not satisfied");
                return false;
            }
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, AShexReport report) {
            // TODO shape external never satisfied
            report.addInfoFailure(shapeExternal, dataNode, null, "Shape external not supported, never satisfied");
            return false;
        }

        @Override
        public Boolean visit(Shape shape, AShexReport report) {
            Set<Triple> accMatchables = new HashSet<>();
            Set<Triple> accNonMatchables = new HashSet<>();
            Util.retrieveRelevantNeighbourhood(vCxt.getGraph(), dataNode, List.of(shape.getTripleExpr()),
                    accMatchables, accNonMatchables, vCxt);

            // TODO we do not want to create the child here, only in specific methods
            //      this specific method could also deal with shapes with extends / wo extends
            AShexReport myReport = report.createChild(dataNode, shape, null);
            if (shape.isClosed() && !accNonMatchables.isEmpty()) {
                myReport.addInfoFailure(shape, dataNode, null, "CLOSED but forbidden triples were found");
                return false;
            } else {
                boolean matches = TripleExprEval.matchesShapeWithoutExtends(accMatchables, shape, vCxt, myReport, dataNode);
                myReport.setSatisfies(matches);
                if (! matches)
                    myReport.addInfoFailure(shape, dataNode, null,
                            "The neighbourhood of the node did not match the triple expression of the shape");
                return matches;
            }
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, AShexReport report) {
            return satisfies(nodeConstraint, dataNode, report);
        }
    }

    // TODO How is this different from the "normal" ShapeExprEval, except for working on neighbourhood instead of a node ?
    // TODO Might be worth extending on ShapeExprEval if the methods become more complex with error reporting, but same as in ShapeExprEval
    static class ExtendsConstraintEvalVisitor implements TypedShapeExprVisitor<Boolean, AShexReport> {

        private final ValidationContext vCxt;
        private final Node dataNode;
        private final Set<Triple> neigh;

        ExtendsConstraintEvalVisitor(Node data, ValidationContext vCxt, Set<Triple> neigh) {
            this.vCxt = vCxt;
            this.dataNode = data;
            this.neigh = neigh;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, AShexReport report) {
            return shapeAnd.getShapeExprs().stream().allMatch(se ->
                    se.visit(this, report));
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, AShexReport report) {
            return satisfiesExtendsConstraint(vCxt.getShapeDecl(shapeExprRef.getLabel()).getShapeExpr(),
                    dataNode, neigh, vCxt, report);
        }

        @Override
        public Boolean visit(Shape shape, AShexReport report) {
            Set<Triple> relevantNeigh = Util.filterRelevantNeighbourhood(neigh, dataNode, shape.getTripleExpr(), vCxt);
            return TripleExprEval.matchesShapeWithoutExtends(relevantNeigh, shape, vCxt, report, dataNode);
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, AShexReport report) {
            return satisfies(nodeConstraint, dataNode, report);
        }

        @Override
        public Boolean visit(ShapeOr shapeOr, AShexReport report) {
            // TODO this could be supported for contexts, see ESWC paper
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, AShexReport report) {
            // TODO this could be supported for contexts, see ESWC paper
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, AShexReport shexReport) {
            throw new UnsupportedOperationException();
        }

    }

    static class NodeConstraintComponentEvalVisitor implements TypedNodeConstraintComponentVisitor<Boolean> {

        private final Node dataNode;
        private final AShexReport report;
        private final NodeConstraint parentConstraint;

        NodeConstraintComponentEvalVisitor(Node dataNode, AShexReport report, NodeConstraint parentConstraint) {
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
                report.addInfoFailure(parentConstraint, dataNode, null,
                        nodeKindCstr + " : Expected " + nodeKind + " for " + displayStr(dataNode));
            return satisfied;
        }

        @Override
        public Boolean visit(DatatypeConstraint datatypeCstr) {
            if (!dataNode.isLiteral()) {
                report.addInfoFailure(parentConstraint, dataNode, null,
                        datatypeCstr + " : Not a literal");
                return false;
            }

            if (datatypeCstr.getDatatypeURI().equals(dataNode.getLiteralDatatypeURI())) {
                // Must be valid for the type
                if (!datatypeCstr.getRDFDatatype().isValid(dataNode.getLiteralLexicalForm())) {
                    report.addInfoFailure(parentConstraint, dataNode, null,
                            datatypeCstr + " : Not valid value : Node " + displayStr(dataNode));
                    return false;
                }
            } else {
                report.addInfoFailure(parentConstraint, dataNode, null,
                        datatypeCstr + " -- Wrong datatype: " + strDatatype(dataNode) + " for focus node: " + displayStr(dataNode));
                return false;
            }
            return true;
        }

        @Override
        public Boolean visit(NumLengthConstraint numLengthCstr) {
            if (!dataNode.isLiteral()) {
                report.addInfoFailure(parentConstraint, dataNode, null,
                        format("NumericConstraint: Not numeric: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }

            RDFDatatype rdfDT = dataNode.getLiteralDatatype();
            if (!(rdfDT instanceof XSDDatatype)) {
                report.addInfoFailure(parentConstraint, dataNode, null,
                        format("NumericConstraint: Not a numeric: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }

            if (XSDDatatype.XSDfloat.equals(rdfDT) || XSDDatatype.XSDdouble.equals(rdfDT)) {
                report.addInfoFailure(parentConstraint, dataNode, null,
                        format("NumericConstraint: Numeric not compatible with xsd:decimal: %s ", ShexLib.displayStr(dataNode)));
                return false;
            }
            String lexicalForm = dataNode.getLiteralLexicalForm();
            if (!rdfDT.isValid(lexicalForm)) {
                report.addInfoFailure(parentConstraint, dataNode, null,
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
            report.addInfoFailure(parentConstraint, dataNode, null, msg);
            return false;
        }

        @Override
        public Boolean visit(NumRangeConstraint numRangeCstr) {
            if (!dataNode.isLiteral()) {
                report.addInfoFailure(parentConstraint, dataNode, null,
                        "NumRange: Not a literal number");
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
            report.addInfoFailure(parentConstraint, dataNode, null, msg);
            return false;
        }

        @Override
        public Boolean visit(StrRegexConstraint strRegexCstr) {
            if (dataNode.isBlank()) {
                String msg = toString() + ": Blank node: " + displayStr(dataNode);
                report.addInfoFailure(parentConstraint, dataNode, null, msg);
                return false;
            }
            String str = NodeFunctions.str(dataNode);
            if (strRegexCstr.getPattern().matcher(str).find()) {
                return true;
            }
            String msg = strRegexCstr + ": Does not match: '" + str + "'";
            report.addInfoFailure(parentConstraint, dataNode, null, msg);
            return false;
        }

        @Override
        public Boolean visit(StrLengthConstraint strLengthCstr) {
            StrLengthKind lengthType = strLengthCstr.getLengthType();
            int length = strLengthCstr.getLength();
            if (!dataNode.isLiteral() && !dataNode.isURI()) {
                String msg = format("%s: Not a literal or URI: %s", lengthType.label(), ShexLib.displayStr(dataNode));
                report.addInfoFailure(parentConstraint, dataNode, null, msg);
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
            report.addInfoFailure(parentConstraint, dataNode, null, msg);
            return false;
        }

        @Override
        public Boolean visit(ValueConstraint valueCstr) {
            boolean b = valueCstr.getValueSetRanges().stream()
                    .anyMatch(valueSetRange -> validateRange(valueSetRange, dataNode));
            if (!b) {
                // TODO why is dataNode null ?
                report.addInfoFailure(parentConstraint, null, null,
                        "Value " + ShexLib.displayStr(dataNode) + " not in range: " + valueCstr);
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
