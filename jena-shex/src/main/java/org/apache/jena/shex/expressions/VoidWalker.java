package org.apache.jena.shex.expressions;

import org.apache.jena.shex.ShexSchema;

import java.util.ArrayList;
import java.util.List;

public class VoidWalker implements VoidTripleExprVisitor, VoidShapeExprVisitor {

    private final List<VoidShapeExprVisitor> shapeExprProcessors;
    private final List<VoidTripleExprVisitor> tripleExprProcessors;
    private final boolean traverseShapes;
    private final boolean traverseTripleConstraints;
    private final boolean followShapeExprRefs;
    private final boolean followTripleExprRefs;
    private final ShexSchema schema;

    public static class Builder {
        private final List<VoidShapeExprVisitor> _shapeExprProcessors = new ArrayList<>();
        private final List<VoidTripleExprVisitor> _tripleExprProcessors = new ArrayList<>();
        private boolean _traverseShapes = false;
        private boolean _traverseTripleConstraints = false;
        private boolean _followShapeExprRefs = false;
        private boolean _followTripleExprRefs = false;
        private ShexSchema _schema = null;

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

        public Builder followTripleExprRefs(ShexSchema schema) {
            this._followTripleExprRefs = true;
            if (this._schema != null && this._schema != schema)
                throw new IllegalArgumentException("A different schema already specified.");
            this._schema = schema;
            return this;
        }

        public VoidWalker build() {
            return new VoidWalker(_shapeExprProcessors, _tripleExprProcessors,
                    _traverseShapes, _traverseTripleConstraints,
                    _followShapeExprRefs, _followTripleExprRefs, _schema);
        }

    }

    private VoidWalker(List<VoidShapeExprVisitor> shapeExprProcessors, List<VoidTripleExprVisitor> tripleExprProcessors,
                      boolean traverseShapes, boolean traverseTripleConstraints,
                      boolean followShapeExprRefs, boolean followTripleExprRefs,
                      ShexSchema schema) {
        this.shapeExprProcessors = shapeExprProcessors;
        this.tripleExprProcessors = tripleExprProcessors;
        this.traverseShapes = traverseShapes;
        this.traverseTripleConstraints = traverseTripleConstraints;
        this.followShapeExprRefs = followShapeExprRefs;
        this.followTripleExprRefs = followTripleExprRefs;
        this.schema = schema;
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

    @Override
    public void visit(ShapeExprRef shapeExprRef) {
        process(shapeExprRef);
        if (followShapeExprRefs)
            schema.get(shapeExprRef.getLabel()).getShapeExpr().visit(this);

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

    @Override
    public void visit(TripleExprRef tripleExprRef) {
        process(tripleExprRef);
        if (followTripleExprRefs)
            schema.getTripleExpr(tripleExprRef.getLabel()).visit(this);
    }

    @Override
    public void visit(TripleConstraint tripleConstraint) {
        process(tripleConstraint);
        if (traverseTripleConstraints)
            tripleConstraint.getValueExpr().visit(this);
    }
}
