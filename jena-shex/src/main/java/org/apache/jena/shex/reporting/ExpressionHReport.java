package org.apache.jena.shex.reporting;


import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
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
    private final List<ReportElement> infos = new ArrayList<>();

    /* package */ ExpressionHReport(Node node, Expression expr, Set<Triple> neighbourhood) {
        super(node, expr, neighbourhood);
//        if (! (expr instanceof NodeConstraint || expr instanceof Shape || expr instanceof ShapeExprRef))
//            throw new IllegalArgumentException("Expression must be an atomic shape expression (node constraint, shape, or reference) or null.");
    }

    public List<ExpressionHReport> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public List<ReportElement> getInfos() {
        return Collections.unmodifiableList(infos);
    }

    /* package */ void addChild(ExpressionHReport child) {
        children.add(child);
    }

    /* package */ void addInfo(ReportElement info) {
        infos.add(info);
    }

    @Override
    public String toString() {
        return toString(this, 2);
    }

    private static String toString(ExpressionHReport r, int indent) {
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
        for (ExpressionHReport child : r.children) {
            sb.append(" ".repeat(indent));
            // TODO indentation
            sb.append(String.format("- %s", ExpressionHReport.toString((ExpressionHReport) child, indent+2)));
        }
        return sb.toString();
    }

}
