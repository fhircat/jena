package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.ShapeExprRef;

import java.util.Set;

/**
 * The result of validating a node against a {@link ShapeDecl} or {@link Shape}.
 */
public class ExprHReportExhaustive extends AtomicExprHReport {


    protected ExprHReportExhaustive(Node node, ShapeExpr expr) {
        super(node, expr);
    }

    public static class Builder extends AtomicExprHReport.Builder {

        private Builder (){}

        @Override
        public AtomicExprHReport.Builder init(Node node, ShapeExpr expr) {
            Builder b = new Builder();
            report = new ExprHReportExhaustive(node, expr);
            return b;
        }

        @Override
        public AtomicExprHReport.Builder init(Node node, ShapeExpr expr, Set<Triple> neighbourhood) {
            return null;
        }

        @Override
        public AtomicExprHReport build() {
            return null;
        }
    }

}
