package org.apache.jena.shex.reporting;


import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Hierarchic report on validating a node or a neighbourhood against an atomic {@link ShapeExpr}.
 */
public abstract class AtomicExprHReport extends ExpressionReport {

    private final List<AtomicExprHReport> children = new ArrayList<>();
    /** Information about validating sub-expressions. */
    private final List<ReportElement> infos = new ArrayList<>();

    protected AtomicExprHReport(Node node, ShapeExpr expr) {
        super(node, expr, null);
        if (! (expr instanceof NodeConstraint || expr instanceof Shape || expr instanceof ShapeExprRef))
            throw new IllegalArgumentException("Expression must be an atomic shape expression (node constraint, shape, or reference) or null.");
    }

    public List<AtomicExprHReport> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public List<ReportElement> getInfos() {
        return Collections.unmodifiableList(infos);
    }

    @Override
    public String toString() {
        return toString(this, 2);
    }


    private static String toString(AtomicExprHReport r, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(" ".repeat(indent));
        sb.append(String.format("Report: node=%s, expr=%s, status=%s", r.node, r.expr, r.getStatus()));
        for (ReportElement info : r.infos) {
            sb.append("\n");
            sb.append(" ".repeat(indent));
            sb.append("details: ");
            sb.append(info.toString());
        }
        for (AtomicExprHReport child : r.children) {
            sb.append(" ".repeat(indent));
            // TODO indentation
            sb.append(String.format("- %s", AtomicExprHReport.toString((AtomicExprHReport) child, indent+2)));
        }
        return sb.toString();
    }

    public static abstract class Builder {

        protected AtomicExprHReport report;

        // TODO parent as argument ?
        /** Creates a new builder of the same type. */
        abstract public Builder init(Node node, ShapeExpr expr);
        /** Creates a new builder of the same type. */
        abstract public Builder init(Node node, ShapeExpr expr, Set<Triple> neighbourhood);
        abstract public AtomicExprHReport build();

        public Builder setSatisfies(boolean satisfies, String message) {
            report.setStatus(satisfies ? ShexStatus.conformant : ShexStatus.nonconformant);
            report.setMessage(message);
            return this;
        }

        public Builder addInfoSuccess(String message) {
            throw new UnsupportedOperationException("not yet implemented");
        }

        public Builder addInfoFailure(NodeConstraintComponent subExpr, String message) {
            report.infos.add(new NodeConstraintReport(subExpr, ShexStatus.nonconformant, message));
            return this;
        }

        public Builder addInfoFailure(Expression subExpr, Set<Triple> subNeigh, String message) {
            ExpressionReport info = new ExpressionReport(report.node, subExpr, subNeigh);
            info.setMessage(message);
            report.infos.add(info);
            return this;
        }

        public Builder addInfoFailure(Expression subExpr, String message) {
            addInfoFailure(subExpr, null, message);
            return this;
        }

    }




}
