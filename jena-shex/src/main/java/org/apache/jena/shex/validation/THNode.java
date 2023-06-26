package org.apache.jena.shex.validation;

import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExpr;

import java.util.List;

public class THNode {

    private final ShapeExpr extendableShape;
    private final Shape mainShape;
    private final List<ShapeExpr> constraints;
    private final SorbeTripleExpr mainShapeSorbeTripleExpr;

    THNode(ShapeExpr extendableShape, Shape mainShape, List<ShapeExpr> constraints, SorbeTripleExpr mainShapeSorbeTripleExpr) {
        this.extendableShape = extendableShape;
        this.mainShape = mainShape;
        this.constraints = constraints;
        this.mainShapeSorbeTripleExpr = mainShapeSorbeTripleExpr;
    }
}
