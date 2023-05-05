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
package org.apache.jena.shex;

import org.apache.jena.shex.expressions.*;

import java.util.*;

public class ShExPathCalculator implements ShapeExprVisitor, TripleExprVisitor, NodeConstraintVisitor {
    protected Map<ShapeExpr, String> shapeExprs = new HashMap<>();
    protected Map<TripleExpression, String> tripleExprs = new HashMap<>();

    public Map<ShapeExpr, String> getShapeExprs() {
        return shapeExprs;
    }

    public Map<TripleExpression, String> getTripleExprs() {
        return tripleExprs;
    }

    protected Stack<String> pathComponents = new Stack<>();

    public ShExPathCalculator(ShexSchema schema) {
        schema.getShapes().forEach( shape->{
            pathComponents.push("@<" + shape.getLabel().toString()+ ">");
            visit(shape.getShapeExpression());
            pathComponents.pop();
        } );
    }

//    @Override
    private void visit(ShapeExpr shapeExpression) {
        shapeExpression.visit(this);
        List<SemAct> semActs = shapeExpression.getSemActs();
        if (semActs != null && !semActs.isEmpty()) {
            shapeExprs.put(shapeExpression, String.join("", pathComponents));
        }
    }

    private void visit(TripleExpression tripleExpression) {
        tripleExpression.visit(this);
        List<SemAct> semActs = tripleExpression.getSemActs();
        if (semActs != null && !semActs.isEmpty()) {
            tripleExprs.put(tripleExpression, String.join("", pathComponents));
        }
    }

    private void walkShapeExprs(List<ShapeExpr> shapeExprs) {
        for (int i = 0; i < shapeExprs.size(); ++i) {
            pathComponents.push("/" + i);
            visit(shapeExprs.get(i));
            pathComponents.pop();
        }
    }

    private void walkTripleExprs(List<TripleExpression> tripleExprs) {
        for (int i = 0; i < tripleExprs.size(); ++i) {
            pathComponents.push("/" + i);
            visit(tripleExprs.get(i));
            pathComponents.pop();
        }
    }

    // ShapeExprVisitor
    @Override
    public void visit(ShapeAnd shape) { walkShapeExprs(shape.expressions()); }

    @Override
    public void visit(ShapeOr shape) { walkShapeExprs(shape.expressions()); }

    @Override
    public void visit(ShapeNot shape) {
        pathComponents.push("NOT/");
        visit(shape.subShape());
        pathComponents.pop();
    }
    public void visit(ShapeExprRef shape) {}
    public void visit(Shape shape) { visit(shape.getTripleExpr()); }
    public void visit(NodeConstraint shape) {}

    // TripleExprVisitor
    public void visit(TripleExprCardinality tripleExpr) { visit(tripleExpr.target()); }
    public void visit(EachOf tripleExpr) { walkTripleExprs(tripleExpr.expressions()); }
    public void visit(OneOf tripleExpr) { walkTripleExprs(tripleExpr.expressions()); }
    public void visit(TripleExprNone tripleExpr) {}
    public void visit(TripleExprRef tripleExpr) {}
    public void visit(TripleConstraint tripleExpr) {
        pathComponents.push("." + tripleExpr.getPredicate().toString());
        visit(tripleExpr.getShapeExpression());
        pathComponents.pop();
    }

    // NodeConstraintVisitor
    public void visit(NodeKindConstraint constraint) {}

    public void visit(DatatypeConstraint constraint) {}

    public void visit(NumLengthConstraint constraint) {}
    public void visit(NumRangeConstraint constraint) {}

    public void visit(StrRegexConstraint constraint) {}
    public void visit(StrLengthConstraint constraint) {}
    public void visit(ValueConstraint constraint) {}
}
