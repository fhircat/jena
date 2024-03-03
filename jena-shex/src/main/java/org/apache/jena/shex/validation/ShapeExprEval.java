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
import org.apache.jena.shex.ShapeDecl;
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

    public static boolean satisfies(ShapeDecl shapeDecl, Node dataNode, ValidationContext vCxt) {
        for (ShapeDecl base : vCxt.getTypeHierarchyGraph().getNonAbstractSubtypes(shapeDecl)) {
            vCxt.startValidate(base, dataNode);
            try {
                ShapeExpr shapeExpr = base.getShapeExpr();
                if (Util.hasExtends(shapeExpr, vCxt::getShapeDecl)) {
                    if (satisfiesWithExtends(base, dataNode, vCxt))
                        return true;
                        // TODO semantic actions and extends ?
                } else {
                    if (satisfies(shapeExpr, dataNode, vCxt) && vCxt.dispatchShapeExprSemanticAction(shapeExpr, dataNode))
                        return true;
                }
            } finally {
                vCxt.finishValidate(base, dataNode);
            }
        }
        return false;
    }

    private static boolean satisfiesWithExtends(ShapeDecl shapeDeclWithExtends, Node dataNode, ValidationContext vCxt) {
        ExtendsShapeExprEvalVisitor evaluator = new ExtendsShapeExprEvalVisitor(dataNode, shapeDeclWithExtends, vCxt);
        return shapeDeclWithExtends.getShapeExpr().visit(evaluator, null);
    }

    /*package*/
    static boolean satisfies(ShapeExpr shapeExpr, Node node, ValidationContext vCxt) {

        ShapeExprEvalVisitor evaluator = new ShapeExprEvalVisitor(node, vCxt);
        return shapeExpr.visit(evaluator, null);
    }

    private static boolean satisfiesExtendsConstraint(ShapeExpr constr, Node node,
                                                      Set<Triple> neigh,
                                                      ValidationContext vCxt) {
        ExtendsConstraintEvalVisitor evaluator = new ExtendsConstraintEvalVisitor(node, vCxt);
        return constr.visit(evaluator, neigh);
    }

    private static boolean satisfies(NodeConstraint nodeConstraint, Node dataNode, ValidationContext vCxt) {
        NodeConstraintComponentEvalVisitor componentEval = new NodeConstraintComponentEvalVisitor(dataNode);
        return nodeConstraint.getComponents().stream().allMatch(ncc -> {
            ReportItem error = ncc.visit(componentEval);
            if (error != null)
                vCxt.reportEntry(error);
            return error == null;
        });
    }

    static class ShapeExprEvalVisitor implements TypedShapeExprVisitor<Boolean, Object> {

        private final ValidationContext vCxt;
        private final Node dataNode;

        ShapeExprEvalVisitor(Node data, ValidationContext vCxt) {
            this.vCxt = vCxt;
            this.dataNode = data;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, Object alwaysNull) {
            return shapeAnd.getShapeExprs().stream().allMatch(se ->
                    se.visit(this, null));
        }

        @Override
        public Boolean visit(ShapeOr shapeOr, Object alwaysNull) {
            boolean result = shapeOr.getShapeExprs().stream().anyMatch(se ->
                    se.visit(this, null));
            if (!result)
                vCxt.reportEntry(new ReportItem("OR expression not satisfied:", dataNode));
            return result;
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, Object alwaysNull) {
            boolean result = !shapeNot.getShapeExpr().visit(this, null);
            if (!result)
                vCxt.reportEntry(new ReportItem("NOT: Term reject because it conforms", dataNode));
            return result;
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, Object alwaysNull) {
            ShapeDecl shapeDecl = vCxt.getShapeDecl(shapeExprRef.getLabel());
            if (vCxt.cycle(dataNode, shapeDecl))
                return true;
            else
                return satisfies(shapeDecl, dataNode, vCxt);
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, Object alwaysNull) {
            // TODO shape external never satisfied
            return false;
        }

        @Override
        public Boolean visit(Shape shape, Object alwaysNull) {
            Set<Triple> accMatchables = new HashSet<>();
            Set<Triple> accNonMatchables = new HashSet<>();
            Util.retrieveRelevantNeighbourhood(vCxt.getGraph(), dataNode, List.of(shape.getTripleExpr()),
                    accMatchables, accNonMatchables, vCxt);

            if (shape.isClosed() && !accNonMatchables.isEmpty())
                return false;

            return TripleExprEval.matchesExpr(accMatchables, shape, vCxt);
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, Object alwaysNull) {
            return satisfies(nodeConstraint, dataNode, vCxt);
        }
    }


    static class ExtendsShapeExprEvalVisitor implements TypedShapeExprVisitor<Boolean, Object> {

        private final Node dataNode;
        private final ShapeDecl shapeDecl;
        private final ValidationContext vCxt;

        ExtendsShapeExprEvalVisitor(Node dataNode, ShapeDecl shapeDecl, ValidationContext vCxt) {
            this.dataNode = dataNode;
            this.shapeDecl = shapeDecl;
            this.vCxt = vCxt;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, Object arg) {
            Pair<Shape, List<ShapeExpr>> mainAndConstr = Util.mainShapeAndConstraints(shapeAnd, vCxt::getShapeDecl);
            return _visit(mainAndConstr.getLeft(), mainAndConstr.getRight());
        }

        @Override
        public Boolean visit(Shape shape, Object arg) {
            return _visit(shape, List.of());
        }

        private Boolean _visit(Shape mainShape, List<ShapeExpr> constraints) {
            // maps extended labels to their respective main shapes
            Map<Node, Shape> baseMainShapes = vCxt.getTypeHierarchyGraph().getSupertypes(shapeDecl).stream()
                    .collect(Collectors.toMap(ShapeDecl::getLabel,
                            sd -> Util.mainShape(sd.getShapeExpr(), vCxt::getShapeDecl)
                    ));

            Set<Triple> accMatchables = new HashSet<>();
            Set<Triple> accNonMatchables = new HashSet<>();
            Util.retrieveRelevantNeighbourhood(vCxt.getGraph(), dataNode,
                    baseMainShapes.values().stream().map(Shape::getTripleExpr).collect(Collectors.toList()),
                    accMatchables, accNonMatchables, vCxt);

            if (mainShape.isClosed() && !accNonMatchables.isEmpty())
                return false;

            Map<Node, Set<Triple>> satisfyingTriples = TripleExprEval.matchesExpr(accMatchables, mainShape,
                    baseMainShapes, vCxt);

            if (satisfyingTriples == null)
                return false;

            for (Node label : baseMainShapes.keySet()) {
                ShapeDecl shapeDecl = vCxt.getShapeDecl(label);
                for (ShapeExpr constr : Util.constraints(shapeDecl.getShapeExpr(), vCxt::getShapeDecl)) {
                    Set<Triple> triples = vCxt.getTypeHierarchyGraph().getSupertypes(shapeDecl).stream()
                            .map(ShapeDecl::getLabel)
                            .flatMap(l -> satisfyingTriples.get(l).stream())
                            .collect(Collectors.toSet());
                    if (!satisfiesExtendsConstraint(constr, dataNode, triples, vCxt))
                        return false;
                }
            }

            /* TODO remove, already considered in the previous loop
            for (ShapeExpr constr : constraints) {
                if (! satisfiesExtendsConstraint(constr, dataNode, satisfyingTriples.get(shapeDecl.getLabel()), vCxt))
                    return false;
            }*/
            return true;
        }


        @Override
        public Boolean visit(ShapeOr shapeOr, Object arg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, Object arg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, Object arg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, Object arg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, Object arg) {
            throw new UnsupportedOperationException();
        }
    }


    static class ExtendsConstraintEvalVisitor implements TypedShapeExprVisitor<Boolean, Set<Triple>> {

        // TODO here we should be able to give the neighbourhood as attribute
        private final ValidationContext vCxt;
        private final Node dataNode;

        ExtendsConstraintEvalVisitor(Node data, ValidationContext vCxt) {
            this.vCxt = vCxt;
            this.dataNode = data;
        }

        @Override
        public Boolean visit(ShapeAnd shapeAnd, Set<Triple> neigh) {
            return shapeAnd.getShapeExprs().stream().allMatch(se ->
                    se.visit(this, neigh));
        }

        @Override
        public Boolean visit(ShapeExprRef shapeExprRef, Set<Triple> neigh) {
            return satisfiesExtendsConstraint(vCxt.getShapeDecl(shapeExprRef.getLabel()).getShapeExpr(),
                    dataNode, neigh, vCxt);
        }

        @Override
        public Boolean visit(Shape shape, Set<Triple> neigh) {

            Set<Triple> relevantNeigh = Util.filterRelevantNeighbourhood(neigh, dataNode, shape.getTripleExpr(), vCxt);
            return TripleExprEval.matchesExpr(relevantNeigh, shape, vCxt);
        }

        @Override
        public Boolean visit(NodeConstraint nodeConstraint, Set<Triple> neigh) {
            return satisfies(nodeConstraint, dataNode, vCxt);
        }

        @Override
        public Boolean visit(ShapeOr shapeOr, Set<Triple> neigh) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeNot shapeNot, Set<Triple> neigh) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean visit(ShapeExternal shapeExternal, Set<Triple> neigh) {
            throw new UnsupportedOperationException();
        }

    }

    static class NodeConstraintComponentEvalVisitor implements TypedNodeConstraintComponentVisitor<ReportItem> {

        private final Node dataNode;

        NodeConstraintComponentEvalVisitor(Node dataNode) {
            this.dataNode = dataNode;
        }

        @Override
        public ReportItem visit(NodeKindConstraint nodeKindCstr) {
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
            return satisfied ? null
                    : new ReportItem(nodeKindCstr + " : Expected " + nodeKind + " for " + displayStr(dataNode),
                    dataNode);
        }

        @Override
        public ReportItem visit(DatatypeConstraint datatypeCstr) {
            if (!dataNode.isLiteral()) {
                return new ReportItem(datatypeCstr + " : Not a literal", dataNode);
            }

            if (datatypeCstr.getDatatypeURI().equals(dataNode.getLiteralDatatypeURI())) {
                // Must be valid for the type
                if (!datatypeCstr.getRDFDatatype().isValid(dataNode.getLiteralLexicalForm())) {
                    String errMsg = datatypeCstr + " : Not valid value : Node " + displayStr(dataNode);
                    return new ReportItem(errMsg, dataNode);
                }
            } else {
                String errMsg = datatypeCstr + " -- Wrong datatype: " + strDatatype(dataNode) + " for focus node: " + displayStr(dataNode);
                return new ReportItem(errMsg, dataNode);
            }
            return null;
        }

        @Override
        public ReportItem visit(NumLengthConstraint numLengthCstr) {
            if (!dataNode.isLiteral()) {
                String msg = format("NumericConstraint: Not numeric: %s ", ShexLib.displayStr(dataNode));
                return new ReportItem(msg, dataNode);
            }

            RDFDatatype rdfDT = dataNode.getLiteralDatatype();
            if (!(rdfDT instanceof XSDDatatype)) {
                String msg = format("NumericConstraint: Not a numeric: %s ", ShexLib.displayStr(dataNode));
                return new ReportItem(msg, dataNode);
            }

            if (XSDDatatype.XSDfloat.equals(rdfDT) || XSDDatatype.XSDdouble.equals(rdfDT)) {
                String msg = format("NumericConstraint: Numeric not compatible with xsd:decimal: %s ", ShexLib.displayStr(dataNode));
                return new ReportItem(msg, dataNode);
            }
            String lexicalForm = dataNode.getLiteralLexicalForm();
            if (!rdfDT.isValid(lexicalForm)) {
                String msg = format("NumericConstraint: Not a valid xsd:decimal: %s ", ShexLib.displayStr(dataNode));
                return new ReportItem(msg, dataNode);
            }

            int N = lexicalForm.length();
            int idx = lexicalForm.indexOf('.');

            switch (numLengthCstr.getLengthType()) {
                case FRACTIONDIGITS: {
                    // Does not include trailing zeros.
                    if (idx < 0) {
                        return null;
                    }
                    //int before = idx;
                    int after = lexicalForm.length() - idx - 1;
                    for (int i = N - 1; i > idx; i--) {
                        if (lexicalForm.charAt(i) != '0')
                            break;
                        after--;
                    }
                    if (after <= numLengthCstr.getLength()) {
                        return null;
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
                        return null;
                    }
                    break;
                }
                default:
                    break;
            }

            String msg = format("Expected %s %d : got = %d", numLengthCstr.getLengthType().label(),
                    numLengthCstr.getLength(), lexicalForm.length());
            return new ReportItem(msg, dataNode);
        }

        @Override
        public ReportItem visit(NumRangeConstraint numRangeCstr) {
            if (!dataNode.isLiteral()) {
                return new ReportItem("NumRange: Not a literal number", dataNode);
            }
            NodeValue nv = NodeValue.makeNode(dataNode);
            int r = NodeValue.compare(nv, numRangeCstr.getNumericValue());

            switch (numRangeCstr.getRangeKind()) {

                case MAXEXCLUSIVE:
                    if (r < 0) {
                        return null;
                    }
                    break;
                case MAXINCLUSIVE:
                    if (r <= 0) {
                        return null;
                    }
                    break;
                case MINEXCLUSIVE:
                    if (r > 0) {
                        return null;
                    }
                    break;
                case MININCLUSIVE:
                    if (r >= 0) {
                        return null;
                    }
                    break;
            }
            String msg = format("Expected %s %s : got = %s", numRangeCstr.getRangeKind().label(), NodeFmtLib.strTTL(nv.getNode()), NodeFmtLib.strTTL(dataNode));
            return new ReportItem(msg, dataNode);
        }

        @Override
        public ReportItem visit(StrRegexConstraint strRegexCstr) {
            if (dataNode.isBlank()) {
                String msg = toString() + ": Blank node: " + displayStr(dataNode);
                return new ReportItem(msg, dataNode);
            }
            String str = NodeFunctions.str(dataNode);
            if (strRegexCstr.getPattern().matcher(str).find()) {
                return null;
            }
            String msg = strRegexCstr + ": Does not match: '" + str + "'";
            return new ReportItem(msg, dataNode);
        }

        @Override
        public ReportItem visit(StrLengthConstraint strLengthCstr) {
            StrLengthKind lengthType = strLengthCstr.getLengthType();
            int length = strLengthCstr.getLength();
            if (!dataNode.isLiteral() && !dataNode.isURI()) {
                String msg = format("%s: Not a literal or URI: %s", lengthType.label(), ShexLib.displayStr(dataNode));
                return new ReportItem(msg, dataNode);
            }
            String str = NodeFunctions.str(dataNode);
            switch (lengthType) {
                case LENGTH:
                    if (str.length() == length) {
                        return null;
                    }
                    break;
                case MAXLENGTH:
                    if (str.length() <= length) {
                        return null;
                    }
                    break;
                case MINLENGTH:
                    if (str.length() >= length) {
                        return null;
                    }
                    break;
            }

            String msg = format("Expected %s %d : got = %d", lengthType.label(), length, str.length());
            return new ReportItem(msg, dataNode);
        }

        @Override
        public ReportItem visit(ValueConstraint valueCstr) {
            boolean b = valueCstr.getValueSetRanges().stream()
                    .anyMatch(valueSetRange -> validateRange(valueSetRange, dataNode));
            if (!b) {
                return new ReportItem("Value " + ShexLib.displayStr(dataNode) + " not in range: " + valueCstr, null);
            }
            return null;
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
