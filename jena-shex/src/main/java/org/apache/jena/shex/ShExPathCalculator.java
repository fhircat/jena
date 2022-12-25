package org.apache.jena.shex;

import org.apache.jena.shex.expressions.*;

import java.util.*;

public class ShExPathCalculator implements ShapeExprVisitor, TripleExprVisitor, NodeConstraintVisitor {
    protected Map<ShapeExpression, String> shapeExprs = new HashMap<>();
    protected Map<TripleExpression, String> tripleExprs = new HashMap<>();

    public Map<ShapeExpression, String> getShapeExprs() {
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
    private void visit(ShapeExpression shapeExpression) {
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

    private void walkShapeExprs(List<ShapeExpression> shapeExprs) {
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
    public void visit(ShapeExprAND shape) { walkShapeExprs(shape.expressions()); }

    @Override
    public void visit(ShapeExprOR shape) { walkShapeExprs(shape.expressions()); }

    @Override
    public void visit(ShapeExprNOT shape) {
        pathComponents.push("NOT/");
        visit(shape.subShape());
        pathComponents.pop();
    }
    public void visit(ShapeExprDot shape) {}
    public void visit(ShapeExprAtom shape) {}
    public void visit(ShapeExprNone shape) {}
    public void visit(ShapeExprRef shape) {}
    public void visit(ShapeExprExternal shape) {}
    public void visit(ShapeExprTripleExpr shape) { visit(shape.getTripleExpr()); }
    public void visit(ShapeNodeConstraint shape) {}

    public void visit(ShapeExprTrue shape) {}
    public void visit(ShapeExprFalse shape) {}

    // TripleExprVisitor
    public void visit(TripleExprCardinality tripleExpr) { visit(tripleExpr.target()); }
    public void visit(TripleExprEachOf tripleExpr) { walkTripleExprs(tripleExpr.expressions()); }
    public void visit(TripleExprOneOf tripleExpr) { walkTripleExprs(tripleExpr.expressions()); }
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
