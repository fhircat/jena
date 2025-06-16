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
 * Hierarchic report on validating a node or a neighbourhood against as {@link Expression}.
 */
public class ExpressionHReport extends ExpressionReport {

    private final List<ExpressionHReport> children = new ArrayList<>();
    private final List<Report> infos = new ArrayList<>();
    private Report refersTo = null;


    /* package */ ExpressionHReport(Node node, Expression expr, Set<Triple> neighbourhood) {
        super(node, expr, neighbourhood);
//        if (! (expr instanceof NodeConstraint || expr instanceof Shape || expr instanceof ShapeExprRef))
//            throw new IllegalArgumentException("Expression must be an atomic shape expression (node constraint, shape, or reference) or null.");
    }

    public void asReferenceTo(Report report, String additionalMessage) {
        this.refersTo = report;
    }

    public List<ExpressionHReport> getChildren() {
        if (refersTo == null)
            return Collections.unmodifiableList(children);
        return Collections.emptyList();
    }

    public List<Report> getInfos() {
        if (refersTo == null)
            return Collections.unmodifiableList(infos);
        return Collections.emptyList();
    }

    /* package */ void addChild(ExpressionHReport child) {
        if (refersTo != null)
            throw new UnsupportedOperationException("Reference report cannot be modified.");
        children.add(child);
    }

    /* package */ void addInfo(Report info) {
        if (refersTo != null)
            throw new UnsupportedOperationException("Reference report cannot be modified.");
        infos.add(info);
    }

    @Override
    public String toString() {
        if (refersTo == null)
            return toString(this, 2);
        return refersTo.toString();
    }

    private static String toString(ExpressionHReport r, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(" ".repeat(indent));
        sb.append("- ");
        String sm = r.getMessage() == null || r.getMessage().isEmpty() ? "" : r.getMessage() + ", ";
        String sd = r.getDetails() == null ? "" : String.format(" [%s] ", r.getDetails().toString());
        sb.append(String.format("%s %s%s%s ?? %s neigh:%s",
                r.getStatus() == ShexStatus.conformant ? "OK" : "KO",
                sm, sd, r.node,
                exprToPrettyString(r.expr),
                r.getSubNeigh()));
        for (Report info : r.infos) {
            sb.append("\n");
            sb.append(" ".repeat(indent));
            sb.append("  additional info: ");
            sb.append(info.toString());
        }
        for (ExpressionHReport child : r.children) {
            sb.append(" ".repeat(indent));
            sb.append(String.format("%s", ExpressionHReport.toString(child, indent+2)));
        }
        return sb.toString();
    }

    // TODO : quick fix, to move elsewhere
    static String exprToPrettyString (Expression expr) {
        if (expr instanceof TripleExpr te)
            return tripleExprToPrettyString(te);
        if (expr instanceof ShapeExpr se)
            return shapeExprToPrettySting(se);
        return expr.toString();
    }

    // TODO : quick fix, to move elsewhere
    static String shapeExprToPrettySting (ShapeExpr expr) {
        if (expr instanceof ShapeExprRef ref)
            return "@" + ref.getLabel();
        if (expr instanceof ShapeNot shapeNot)
            return "NOT " + shapeExprToPrettySting(shapeNot.getShapeExpr());
        if (expr instanceof ShapeAnd shapeAnd)
            return String.join(" AND ",
                    shapeAnd.getShapeExprs().stream()
                            .map(ExpressionHReport::shapeExprToPrettySting)
                            .toArray(String[]::new));

        if (expr instanceof ShapeOr shapeOr)
            return String.join(" AND ",
                    shapeOr.getShapeExprs().stream()
                            .map(ExpressionHReport::shapeExprToPrettySting)
                            .toArray(String[]::new));

        if (expr instanceof Shape shape) {
            String extras = shape.getExtras().isEmpty()
                    ? ""
                    : "EXTRA " + shape.getExtras();
            String closed = shape.isClosed() ? "CLOSED" : "";
            String xtends = shape.getExtends().isEmpty()
                    ? ""
                    : "EXTEND " + shape.getExtends();
            return String.format("%s %s %s %s", xtends, closed, extras,
                    tripleExprToPrettyString(shape.getTripleExpr()));
        }
        if (expr instanceof NodeConstraint nc) {
            String s = nc.toString();
            return s.substring(0, s.length() - "/NodeConstraint".length());
        }
        return expr.toString();
    }

    // TODO : quick fix, to move elsewhere
    static String tripleExprToPrettyString (TripleExpr expr) {
        if (expr instanceof TripleConstraint tripleConstr) {
            String p = tripleConstr.getPredicate().toString();
            String pp = p.substring(p.lastIndexOf('/')+1);
            return String.format("ex:%s %s", pp, shapeExprToPrettySting(tripleConstr.getValueExpr()));
        }
        if (expr instanceof EachOf eachOf)
            return String.join (" ; ",
                    eachOf.getTripleExprs().stream()
                            .map(ExpressionHReport::tripleExprToPrettyString)
                            .toArray(String[]::new));
        if (expr instanceof OneOf oneOf)
            return String.join (" ; ",
                    oneOf.getTripleExprs().stream()
                            .map(ExpressionHReport::tripleExprToPrettyString)
                            .toArray(String[]::new));
        if (expr instanceof TripleExprCardinality card)
            return String.format("%s [%d, %d]",
                    card.getSubExpr(),
                    card.getCardinality().min,
                    card.getCardinality().max);
        return expr.toString();
    }
}
