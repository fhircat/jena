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

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.util.UndefinedReferenceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VoidWalker implements VoidTripleExprVisitor, VoidShapeExprVisitor {

    private final List<VoidShapeExprVisitor> shapeExprProcessors;
    private final List<VoidTripleExprVisitor> tripleExprProcessors;
    private final boolean traverseShapes;
    private final boolean traverseTripleConstraints;
    private final boolean followShapeExprRefs;
    private final boolean followTripleExprRefs;
    private final ShexSchema schema;
    private final Map<Node, ShapeDecl> shapeExprRefDefMap;
    private final Map<Node, TripleExpr> tripleExprRefDefMap;

    public static class Builder {
        private final List<VoidShapeExprVisitor> _shapeExprProcessors = new ArrayList<>();
        private final List<VoidTripleExprVisitor> _tripleExprProcessors = new ArrayList<>();
        private boolean _traverseShapes = false;
        private boolean _traverseTripleConstraints = false;
        private boolean _followShapeExprRefs = false;
        private boolean _followTripleExprRefs = false;
        private ShexSchema _schema = null;
        private Map<Node, ShapeDecl> _shapeExprRefDefMap = null;
        private Map<Node, TripleExpr> _tripleExprRefDefMap = null;


        public Builder processShapeExprsWith(VoidShapeExprVisitor shapeExprProcessor) {
            this._shapeExprProcessors.add(shapeExprProcessor);
            return this;
        }

        public Builder processTripleExprsWith(VoidTripleExprVisitor tripleExprProcessor) {
            this._tripleExprProcessors.add(tripleExprProcessor);
            return this;
        }

        public Builder traverseShapes () {
            this._traverseShapes = true;
            return this;
        }

        public Builder traverseTripleConstraints () {
            this._traverseTripleConstraints = true;
            return this;
        }

        public Builder followShapeExprRefs(ShexSchema schema) {
            this._followShapeExprRefs = true;
            if (this._schema != null && this._schema != schema)
                throw new IllegalArgumentException("A different schema already specified.");
            this._schema = schema;
            return this;
        }

        public Builder followShapeExprRefs(Map<Node, ShapeDecl> shapeExprRefDefMap) {
            this._followShapeExprRefs = true;
            this._shapeExprRefDefMap = shapeExprRefDefMap;
            return this;
        }

        public Builder followTripleExprRefs(ShexSchema schema) {
            this._followTripleExprRefs = true;
            if (this._schema != null && this._schema != schema)
                throw new IllegalArgumentException("A different schema already specified.");
            this._schema = schema;
            return this;
        }

        public Builder followTripleExprRefs(Map<Node, TripleExpr> tripleExprRefDefMap) {
            this._followTripleExprRefs = true;
            this._tripleExprRefDefMap = tripleExprRefDefMap;
            return this;
        }

        public VoidWalker build() {
            return new VoidWalker(_shapeExprProcessors, _tripleExprProcessors,
                    _traverseShapes, _traverseTripleConstraints,
                    _followShapeExprRefs, _followTripleExprRefs,
                    _schema, _shapeExprRefDefMap, _tripleExprRefDefMap);
        }

    }

    private VoidWalker(List<VoidShapeExprVisitor> shapeExprProcessors, List<VoidTripleExprVisitor> tripleExprProcessors,
                       boolean traverseShapes, boolean traverseTripleConstraints,
                       boolean followShapeExprRefs, boolean followTripleExprRefs,
                       ShexSchema schema,
                       Map<Node, ShapeDecl> shapeExprRefDefMap, Map<Node, TripleExpr> tripleExprRefDefMap) {
        this.shapeExprProcessors = shapeExprProcessors;
        this.tripleExprProcessors = tripleExprProcessors;
        this.traverseShapes = traverseShapes;
        this.traverseTripleConstraints = traverseTripleConstraints;
        this.followShapeExprRefs = followShapeExprRefs;
        this.followTripleExprRefs = followTripleExprRefs;
        this.schema = schema;
        this.shapeExprRefDefMap = shapeExprRefDefMap;
        this.tripleExprRefDefMap = tripleExprRefDefMap;
    }

    private void process(TripleExpr tripleExpr) {
        tripleExprProcessors.forEach(v -> tripleExpr.visit(v));
    }

    private void process(ShapeExpr shapeExpr) {
        shapeExprProcessors.forEach(v -> shapeExpr.visit(v));
    }

    @Override
    public void visit(ShapeAnd shapeAnd) {
        process(shapeAnd);
        shapeAnd.getShapeExprs().forEach(sh->sh.visit(this));
    }

    @Override
    public void visit(ShapeOr shapeOr) {
        process(shapeOr);
        shapeOr.getShapeExprs().forEach(sh->sh.visit(this));
    }

    @Override
    public void visit(ShapeNot shapeNot) {
        process(shapeNot);
        shapeNot.getShapeExpr().visit(this);
    }

    /**
     * @throws UndefinedReferenceException If the visited reference is undefined.
     */
    @Override
    public void visit(ShapeExprRef shapeExprRef) {
        process(shapeExprRef);
        if (followShapeExprRefs) {
           ShapeDecl shapeDecl = schema != null
                   ? schema.get(shapeExprRef.getLabel())
                   : shapeExprRefDefMap.get(shapeExprRef.getLabel());
            if (shapeDecl == null)
                throw new UndefinedReferenceException(shapeExprRef.getLabel(), "Undefined tripleExprRef " + shapeExprRef.getLabel());
           shapeDecl.getShapeExpr().visit(this);
        }
    }

    @Override
    public void visit(ShapeExternal shapeExternal) {
        process(shapeExternal);
    }

    @Override
    public void visit(Shape shape) {
        process(shape);
        if (traverseShapes)
            shape.getTripleExpr().visit(this);
    }

    @Override
    public void visit(NodeConstraint nodeConstraint) {
        process(nodeConstraint);
    }

    @Override
    public void visit(TripleExprCardinality tripleExprCardinality) {
        process(tripleExprCardinality);
        tripleExprCardinality.getSubExpr().visit(this);
    }

    @Override
    public void visit(EachOf eachOf) {
        process(eachOf);
        eachOf.getTripleExprs().forEach(sh->sh.visit(this));
    }

    @Override
    public void visit(OneOf oneOf) {
        process(oneOf);
        oneOf.getTripleExprs().forEach(sh->sh.visit(this));
    }

    @Override
    public void visit(TripleExprEmpty tripleExprEmpty) {
        process(tripleExprEmpty);
    }

    /**
     * @throws UndefinedReferenceException If the visited reference is undefined.
     */
    @Override
    public void visit(TripleExprRef tripleExprRef) {
        process(tripleExprRef);
        if (followTripleExprRefs) {
            TripleExpr tripleExpr = schema != null
                    ? schema.getTripleExpr(tripleExprRef.getLabel())
                    : tripleExprRefDefMap.get(tripleExprRef.getLabel());
            if (tripleExpr == null)
                throw new UndefinedReferenceException(tripleExprRef.getLabel(), "Undefined tripleExprRef " + tripleExprRef.getLabel());
            tripleExpr.visit(this);
        }
    }

    @Override
    public void visit(TripleConstraint tripleConstraint) {
        process(tripleConstraint);
        if (traverseTripleConstraints)
            tripleConstraint.getValueExpr().visit(this);
    }
}
