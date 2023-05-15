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

// TODO never used with non null after, used only once as such. Worth keeping ?
public class TripleExprWalker implements TripleExprVisitor {

    private final TripleExprVisitor beforeVisitor;
    private final TripleExprVisitor afterVisitor;
    private final ShapeExprVisitor shapeVisitor;

    public TripleExprWalker(TripleExprVisitor beforeVisitor,
                            TripleExprVisitor afterVisitor,
                            ShapeExprVisitor shapeVisitor) {
        this.beforeVisitor = beforeVisitor;
        this.afterVisitor = afterVisitor;
        this.shapeVisitor = shapeVisitor;
    }

    private void before(TripleExpr tripleExpr) {
        if ( beforeVisitor != null )
            tripleExpr.visit(beforeVisitor);
    }

    private void after(TripleExpr tripleExpr) {
        if ( afterVisitor != null )
            tripleExpr.visit(afterVisitor);
    }

    @Override
    public void visit(EachOf eachOf) {
        before(eachOf);
        eachOf.getTripleExprs().forEach(tripleExpr->tripleExpr.visit(this));
        after(eachOf);
    }

    @Override
    public void visit(OneOf oneOf) {
        before(oneOf);
        oneOf.getTripleExprs().forEach(tripleExpr->tripleExpr.visit(this));
        after(oneOf);
    }

    @Override
    public void visit(TripleExprCardinality tripleExprCardinality) {
        before(tripleExprCardinality);
        tripleExprCardinality.getSubExpr().visit(this);
        after(tripleExprCardinality);

    }

    @Override public void visit(TripleExprEmpty tripleExprEmpty) {
        before(tripleExprEmpty);
        after(tripleExprEmpty);
    }

    @Override public void visit(TripleExprRef tripleExprRef) {
        before(tripleExprRef);
        after(tripleExprRef);
    }

    @Override public void visit(TripleConstraint tripleConstraint) {
        before(tripleConstraint);
        if ( shapeVisitor != null )
            tripleConstraint.getValueExpr().visit(shapeVisitor);
        after(tripleConstraint);
    }
}
