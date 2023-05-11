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

package org.apache.jena.shex.expressions;

public class ShapeExprWalker implements ShapeExprVisitor {

    private final ShapeExprVisitor beforeVisitor;
    private final ShapeExprVisitor afterVisitor;
    private final TripleExprVisitor tripleExprWalker;
    private NodeConstraintComponentVisitor nodeConstraintVisitor;

    public ShapeExprWalker(ShapeExprVisitor beforeVisitor, ShapeExprVisitor afterVisitor,
                           TripleExprVisitor beforeTripleExprVisitor, TripleExprVisitor afterTripleExprVisitor,
                           NodeConstraintComponentVisitor nodeConstraintVisitor) {
        this.beforeVisitor = beforeVisitor;
        this.afterVisitor = afterVisitor;
        // Walker because TripleExpr can contain a ShapeExpression
        this.tripleExprWalker = new TripleExprWalker(beforeTripleExprVisitor, afterTripleExprVisitor, this);
        // XXX [NodeConstraint] - no recursion.
        this.nodeConstraintVisitor = nodeConstraintVisitor;
    }


    private void before(ShapeExpr shape) {
        if ( beforeVisitor != null )
            shape.visit(beforeVisitor);
    }

    private void after(ShapeExpr shape) {
        if ( afterVisitor != null )
            shape.visit(afterVisitor);
    }

    @Override public void visit(ShapeAnd shapeAnd) {
        before(shapeAnd);
        shapeAnd.getShapeExprs().forEach(sh->sh.visit(this));
        after(shapeAnd);
    }

    @Override public void visit(ShapeOr shapeOr) {
        before(shapeOr);
        shapeOr.getShapeExprs().forEach(sh->sh.visit(this));
        after(shapeOr);
    }

    @Override public void visit(ShapeNot shapeNot) {
        before(shapeNot);
        shapeNot.getShapeExpr().visit(this);
        after(shapeNot);
    }

    @Override
    public void visit(ShapeExprRef shapeExprRef) {
        before(shapeExprRef);
        after(shapeExprRef);
    }

    @Override
    public void visit(ShapeExternal shapeExternal) {
        before(shapeExternal);
        after(shapeExternal);
    }

    @Override
    public void visit(Shape shape) {
        before(shape);
        if ( tripleExprWalker != null && shape.getTripleExpr() != null )
            shape.getTripleExpr().visit(tripleExprWalker);
        after(shape);
    }

    @Override
    public void visit(NodeConstraint nodeConstraint) {
        before(nodeConstraint);
        if ( nodeConstraintVisitor != null)
            nodeConstraint.getComponents().forEach(ncc->ncc.visit(nodeConstraintVisitor));
        after(nodeConstraint);
    }
}
