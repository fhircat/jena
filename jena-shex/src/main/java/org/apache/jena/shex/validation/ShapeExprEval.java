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
import org.apache.jena.shex.ShexReport2;
import org.apache.jena.shex.calc.TypedNodeConstraintComponentVisitor;
import org.apache.jena.shex.calc.TypedShapeExprVisitor;
import org.apache.jena.shex.calc.Util;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.sys.ReportItem;
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

    public static ShexReport2 satisfies (ShapeDecl shapeDecl, Node dataNode,
                                         ValidationContext vCxt) {
        ShexReport2 report = ShexReport2.get(shapeDecl, dataNode);
        report.setSatisfies(_satisfies(shapeDecl, dataNode, vCxt, report));
        return report;
    }

    /* package */
    static boolean satisfies (ShapeDecl shapeDecl, Node dataNode, ValidationContext vCxt,
                              ShexReport2 parentReport) {
        ShexReport2 childReport = parentReport.createChild(shapeDecl, dataNode);
        boolean satisfies = _satisfies(shapeDecl, dataNode, vCxt, childReport);
        childReport.setSatisfies(satisfies);
        return satisfies;
    }

    private static boolean _satisfies(ShapeDecl shapeDecl, Node dataNode,
                                     ValidationContext vCxt, ShexReport2 shexReport) {
        for (ShapeDecl base : vCxt.getTypeHierarchyGraph().getNonAbstractSubtypes(shapeDecl)) {
            vCxt.startValidate(base, dataNode);
            try {
                ShapeExpr shapeExpr = base.getShapeExpr();
                if (Util.hasExtends(shapeExpr, vCxt::getShapeDecl)) {
                    if (satisfiesWithExtends(base, dataNode, vCxt, shexReport)) {
                        return true;
                        // TODO semantic actions and extends ?
                    }
                } else {
                    // TODO report for semantic actions
                    if (satisfies(shapeExpr, dataNode, vCxt, shexReport) && vCxt.dispatchShapeExprSemanticAction(shapeExpr, dataNode)) {
                        return true;
                    }
                }
            } finally {
                vCxt.finishValidate(base, dataNode);
            }
        }
        return false;
    }

    /*package*/
    static boolean satisfies(ShapeExpr shapeExpr, Node node, ValidationContext vCxt, ShexReport2 shexReport) {

        ShapeExprEvalVisitor evaluator = new ShapeExprEvalVisitor(node, vCxt);
        return shapeExpr.visit(evaluator, shexReport);
    }

    private static boolean satisfiesWithExtends(ShapeDecl shapeDeclWithExtends, Node dataNode,
                                                ValidationContext vCxt, ShexReport2 shexReport) {

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
        if (mainShape.isClosed() && !accNonMatchables.isEmpty()) {
            shexReport.addReport("CLOSED required but forbidden triples");
            return false;
        }

        Map<Node, Set<Triple>> satisfyingTriples = TripleExprEval.matchesShapeWithExtends(accMatchables, mainShape,
                baseMainShapes, vCxt, shexReport);

        if (satisfyingTriples == null) {
            shexReport.addReport("The neighbourhood didn't match the extension hierarchy");
            return false;
        }

        for (Node label : baseMainShapes.keySet()) {
            ShapeDecl shapeDecl = vCxt.getShapeDecl(label);
            for (ShapeExpr constr : Util.constraints(shapeDecl.getShapeExpr(), vCxt::getShapeDecl)) {
                Set<Triple> triples = vCxt.getTypeHierarchyGraph().getSupertypes(shapeDecl).stream()
                        .map(ShapeDecl::getLabel)
                        .flatMap(l -> satisfyingTriples.get(l).stream())
                        .collect(Collectors.toSet());
                if (!satisfiesExtendsConstraint(constr, dataNode, triples, vCxt, shexReport)) {
                    shexReport.addReport("The part of the neighbourhood did not match the constraints");
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean satisfiesExtendsConstraint(ShapeExpr constr, Node node,
                                                      Set<Triple> neigh,
                                                      ValidationContext vCxt,
                                                      ShexReport2 shexReport) {
        ExtendsConstraintEvalVisitor evaluator = new ExtendsConstraintEvalVisitor(node, vCxt, neigh);
        return constr.visit(evaluator, shexReport);
    }

    // TODO parameter vCxt not used
    private static boolean satisfies(NodeConstraint nodeConstraint, Node dataNode,
                                     ShexReport2 shexReport) {
        NodeConstraintComponentEvalVisitor componentEval = new NodeConstraintComponentEvalVisitor(dataNode, shexReport);
        return nodeConstraint.getComponents().stream().allMatch(ncc -> ncc.visit(componentEval));
    }

    // TODO report for all visit functions
    // TODO possible to instantiate the second generic type parameter by ShexReport2
    static class ShapeExprEvalVisitor implements TypedShapeExprVisitor<Boolean, ShexReport2> {

        private final ValidationContext vCxt;
        private final Node dataNode;

        ShapeExprEvalVisitor(Node data, ValidationContext vCxt) {
            this.vCxt = vCxt;
            this.dataNode = data;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, ShexReport2 shexReport) {
            for (ShapeExpr se : shapeAnd.getShapeExprs()) {
                if (! se.visit(this, shexReport)) {
                    shexReport.addReport("AND not satisfied");
                    return false;
                }
            }
            return true;
        }

        @Override
        public Boolean visit(ShapeOr shapeOr, ShexReport2 shexReport) {
            for (ShapeExpr se : shapeOr.getShapeExprs()) {
                if (se.visit(this, shexReport))
                    return true;
            }
            shexReport.addReport("None of the OR disjuncts was satisfied");
            return false;
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, ShexReport2 shexReport) {
            if (shapeNot.getShapeExpr().visit(this, shexReport)) {
                shexReport.addReport("Negated expression is satisfied");
                return false;
            } else
                return true;
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, ShexReport2 shexReport) {
            // TODO create a new report : satisfies
            ShapeDecl shapeDecl = vCxt.getShapeDecl(shapeExprRef.getLabel());
            if (vCxt.cycle(dataNode, shapeDecl))
                return true;
            else if (satisfies(shapeDecl, dataNode, vCxt, shexReport))
                return true;
            else {
                shexReport.addReport("Shape reference not satisfied");
                return false;
            }
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, ShexReport2 shexReport) {
            // TODO shape external never satisfied
            shexReport.addReport("Shape external not supported, never satisfied");
            return false;
        }

        @Override
        public Boolean visit(Shape shape, ShexReport2 shexReport) {
            Set<Triple> accMatchables = new HashSet<>();
            Set<Triple> accNonMatchables = new HashSet<>();
            Util.retrieveRelevantNeighbourhood(vCxt.getGraph(), dataNode, List.of(shape.getTripleExpr()),
                    accMatchables, accNonMatchables, vCxt);

            ShexReport2 myReport = shexReport.createChild(shape, dataNode);
            if (shape.isClosed() && !accNonMatchables.isEmpty()) {
                myReport.addReport("CLOSED but forbidden triples");
                return false;
            } else {
                boolean matches = TripleExprEval.matchesShapeWithoutExtends(accMatchables, shape, vCxt, myReport);
                myReport.setSatisfies(matches);
                if (! matches)
                    myReport.addReport("The neighbourhood of the node did not match the triple expression");
                return matches;
            }
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, ShexReport2 shexReport) {
            return satisfies(nodeConstraint, dataNode, shexReport);
        }
    }

    // TODO How is this different from the "normal" ShapeExprEval, except for working on neighbourhood instead of a node ?
    // TODO Might be worth extending on ShapeExprEval if the methods become more complex with error reporting, but same as in ShapeExprEval
    static class ExtendsConstraintEvalVisitor implements TypedShapeExprVisitor<Boolean, ShexReport2> {

        private final ValidationContext vCxt;
        private final Node dataNode;
        private final Set<Triple> neigh;

        ExtendsConstraintEvalVisitor(Node data, ValidationContext vCxt, Set<Triple> neigh) {
            this.vCxt = vCxt;
            this.dataNode = data;
            this.neigh = neigh;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, ShexReport2 shexReport) {
            return shapeAnd.getShapeExprs().stream().allMatch(se ->
                    se.visit(this, shexReport));
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, ShexReport2 shexReport) {
            return satisfiesExtendsConstraint(vCxt.getShapeDecl(shapeExprRef.getLabel()).getShapeExpr(),
                    dataNode, neigh, vCxt, shexReport);
        }

        @Override
        public Boolean visit(Shape shape, ShexReport2 shexReport) {
            Set<Triple> relevantNeigh = Util.filterRelevantNeighbourhood(neigh, dataNode, shape.getTripleExpr(), vCxt);
            return TripleExprEval.matchesShapeWithoutExtends(relevantNeigh, shape, vCxt, shexReport);
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, ShexReport2 shexReport) {
            return satisfies(nodeConstraint, dataNode, shexReport);
        }

        @Override
        public Boolean visit(ShapeOr shapeOr, ShexReport2 shexReport) {
            // TODO this could be supported for contexts, see ESWC paper
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, ShexReport2 shexReport) {
            // TODO this could be supported for contexts, see ESWC paper
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, ShexReport2 shexReport) {
            throw new UnsupportedOperationException();
        }

    }

    // TODO make it coherent, passing the report as parameter instead of returning it
    static class NodeConstraintComponentEvalVisitor implements TypedNodeConstraintComponentVisitor<Boolean> {

        private final Node dataNode;
        private final ShexReport2 shexReport;

        NodeConstraintComponentEvalVisitor(Node dataNode, ShexReport2 shexReport) {
            this.dataNode = dataNode;
            this.shexReport = shexReport;
        }

        @Override
        public Boolean visit(NodeKindConstraint nodeKindCstr) {
            NodeKind nodeKind = nodeKindCstr.getNodeKind();
            boolean satisfied = true;

            switch (nodeKind) {
                case BNODE:
                    satisfied = dataNode.isBlank();
                    break;
                case IRI:
                    satisfied = dataNode.isURI();
                    break;
                case LITERAL:
                    satisfied = dataNode.isLiteral();
                    break;
                case NONLITERAL:
                    satisfied = !dataNode.isLiteral();
                    break;
            }
            // TODO Bad.
            if (!satisfied)
                shexReport.addNodeConstraintReport(
                        nodeKindCstr + " : Expected " + nodeKind + " for " + displayStr(dataNode),
                        dataNode);
            return satisfied;
        }

        @Override
        public Boolean visit(DatatypeConstraint datatypeCstr) {
            if (!dataNode.isLiteral()) {
                shexReport.addNodeConstraintReport(datatypeCstr + " : Not a literal", dataNode);
                return false;
            }

            if (datatypeCstr.getDatatypeURI().equals(dataNode.getLiteralDatatypeURI())) {
                // Must be valid for the type
                if (!datatypeCstr.getRDFDatatype().isValid(dataNode.getLiteralLexicalForm())) {
                    shexReport.addNodeConstraintReport(
                            datatypeCstr + " : Not valid value : Node " + displayStr(dataNode),
                            dataNode);
                    return false;
                }
            } else {
                shexReport.addNodeConstraintReport(
                        datatypeCstr + " -- Wrong datatype: " + strDatatype(dataNode) + " for focus node: " + displayStr(dataNode),
                        dataNode);
                return false;
            }
            return true;
        }

        @Override
        public Boolean visit(NumLengthConstraint numLengthCstr) {
            if (!dataNode.isLiteral()) {
                shexReport.addNodeConstraintReport(format("NumericConstraint: Not numeric: %s ", ShexLib.displayStr(dataNode)),
                    dataNode);
                return false;
            }

            RDFDatatype rdfDT = dataNode.getLiteralDatatype();
            if (!(rdfDT instanceof XSDDatatype)) {
                shexReport.addNodeConstraintReport(format("NumericConstraint: Not a numeric: %s ", ShexLib.displayStr(dataNode)),
                    dataNode);
                return false;
            }

            if (XSDDatatype.XSDfloat.equals(rdfDT) || XSDDatatype.XSDdouble.equals(rdfDT)) {
                shexReport.addNodeConstraintReport(format("NumericConstraint: Numeric not compatible with xsd:decimal: %s ", ShexLib.displayStr(dataNode)),
                    dataNode);
                return false;
            }
            String lexicalForm = dataNode.getLiteralLexicalForm();
            if (!rdfDT.isValid(lexicalForm)) {
                shexReport.addNodeConstraintReport(
                        format("NumericConstraint: Not a valid xsd:decimal: %s ", ShexLib.displayStr(dataNode)),
                        dataNode);
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
            shexReport.addNodeConstraintReport(msg, dataNode);
            return false;
        }

        @Override
        public Boolean visit(NumRangeConstraint numRangeCstr) {
            if (!dataNode.isLiteral()) {
                shexReport.addNodeConstraintReport("NumRange: Not a literal number", dataNode);
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
            shexReport.addNodeConstraintReport(msg, dataNode);
            return false;
        }

        @Override
        public Boolean visit(StrRegexConstraint strRegexCstr) {
            if (dataNode.isBlank()) {
                String msg = toString() + ": Blank node: " + displayStr(dataNode);
                shexReport.addNodeConstraintReport(msg, dataNode);
                return false;
            }
            String str = NodeFunctions.str(dataNode);
            if (strRegexCstr.getPattern().matcher(str).find()) {
                return true;
            }
            String msg = strRegexCstr + ": Does not match: '" + str + "'";
            shexReport.addNodeConstraintReport(msg, dataNode);
            return false;
        }

        @Override
        public Boolean visit(StrLengthConstraint strLengthCstr) {
            StrLengthKind lengthType = strLengthCstr.getLengthType();
            int length = strLengthCstr.getLength();
            if (!dataNode.isLiteral() && !dataNode.isURI()) {
                String msg = format("%s: Not a literal or URI: %s", lengthType.label(), ShexLib.displayStr(dataNode));
                shexReport.addNodeConstraintReport(msg, dataNode);
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
            shexReport.addNodeConstraintReport(msg, dataNode);
            return false;
        }

        @Override
        public Boolean visit(ValueConstraint valueCstr) {
            boolean b = valueCstr.getValueSetRanges().stream()
                    .anyMatch(valueSetRange -> validateRange(valueSetRange, dataNode));
            if (!b) {
                // TODO why is dataNode null ?
                shexReport.addNodeConstraintReport("Value " + ShexLib.displayStr(dataNode) + " not in range: " + valueCstr, null);
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
