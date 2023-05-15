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

package org.apache.jena.shex.eval;

import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.*;

public class TripleExprWalker2 implements TripleExprVisitor2<Void> {

    private final TripleExprVisitor2<Void> beforeVisitor;
    private final TripleExprVisitor2<Void> afterVisitor;
    private final boolean traverseReferences;
    private final ShexSchema schema;

    public TripleExprWalker2(TripleExprVisitor2<Void> beforeVisitor,
                             TripleExprVisitor2<Void> afterVisitor,
                             boolean traverseReferences,
                             ShexSchema schema) {
        this.beforeVisitor = beforeVisitor;
        this.afterVisitor = afterVisitor;
        this.traverseReferences = traverseReferences;
        this.schema = schema;
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
    public Void visit(EachOf eachOf) {
        before(eachOf);
        eachOf.getTripleExprs().forEach(tripleExpr->tripleExpr.visit(this));
        after(eachOf);
        return null;
    }

    @Override
    public Void visit(OneOf oneOf) {
        before(oneOf);
        oneOf.getTripleExprs().forEach(tripleExpr->tripleExpr.visit(this));
        after(oneOf);
        return null;
    }

    @Override
    public Void visit(TripleExprCardinality tripleExprCardinality) {
        before(tripleExprCardinality);
        tripleExprCardinality.getSubExpr().visit(this);
        after(tripleExprCardinality);
        return null;
    }

    @Override public Void visit(TripleExprEmpty tripleExprEmpty) {
        before(tripleExprEmpty);
        after(tripleExprEmpty);
        return null;
    }

    @Override public Void visit(TripleExprRef tripleExprRef) {
        before(tripleExprRef);
        if (traverseReferences)
            schema.getTripleExpression(tripleExprRef.getLabel()).visit(this);
        after(tripleExprRef);
        return null;

    }

    @Override public Void visit(TripleConstraint tripleConstraint) {
        before(tripleConstraint);
        after(tripleConstraint);
        return null;
    }
}
