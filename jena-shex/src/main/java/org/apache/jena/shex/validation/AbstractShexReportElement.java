package org.apache.jena.shex.validation;


import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of validating a node against a {@link ShapeDecl} or a {@link Shape}.
 * Is a hierarchic structure.
 */
public abstract class AbstractShexReportElement implements ShexReportElement {

    /* TODO make ShexReport subclass of this one, and call it ExhaustiveShapeReporter or something like that
            ie all report infos are registered. Possibly, make also a minimal implementation that ignores all
            registered infos. */

    private final Node node;
    private final ShapeExpr expr;
    private ShexStatus status = null;

    private ShexReportElement parent = null;
    private final List<ShexReportElement> children = new ArrayList<>();

    protected final List<ReportInfo> infos = new ArrayList<>();

    // TODO expr should be null only for start semantic actions
    protected AbstractShexReportElement(Node node, ShapeExpr expr, ShexReportElement parent) {
        if (! (expr == null || expr instanceof NodeConstraint || expr instanceof Shape || expr instanceof ShapeExprRef))
            throw new IllegalArgumentException("Expression must be an atomic shape expression (node constraint, shape, or reference) or null.");
        this.node = node;
        this.expr = expr;
        this.parent = parent;
    }

    // TODO quick fix for factory and Start SemActs, to remove
    protected AbstractShexReportElement() {
        this.node = null;
        this.expr = null;
    }

    @Override
    public void setParent (ShexReportElement parent) {
        if (this.parent != null)
            throw new IllegalStateException("Can't set parent twice");
        this.parent = parent;
    }

    public abstract AbstractShexReportElement createChild(Node node, ShapeExpr expr);

    @Override
    public void setSatisfies(boolean satisfies) {
        this.status = satisfies ? ShexStatus.conformant : ShexStatus.nonconformant;
    }

    @Override
    public ShexStatus getStatus() {
        return status;
    }

    @Override
    public List<ReportInfo> getInfos() {
        return Collections.unmodifiableList(infos);
    }

    @Override
    public List<ShexReportElement> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public ShexReportElement getParent() {
        return parent;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public ShapeExpr getShapeExpr() {
        return expr;
    }

    @Override
    public String toString() {
        return toString(this, 2);
    }



    private static String toString(AbstractShexReportElement r, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(" ".repeat(indent));
        sb.append(String.format("Report: node=%s, expr=%s, status=%s", r.node, r.expr, r.status));
        for (ReportInfo info : r.infos) {
            sb.append("\n");
            sb.append(" ".repeat(indent));
            sb.append("details: ");
            sb.append(info.toString());
        }
        for (ShexReportElement child : r.children) {
            sb.append(" ".repeat(indent));
            // TODO indentation
            sb.append(String.format("- %s", AbstractShexReportElement.toString((AbstractShexReportElement) child, indent+2)));
        }
        return sb.toString();
    }
}
