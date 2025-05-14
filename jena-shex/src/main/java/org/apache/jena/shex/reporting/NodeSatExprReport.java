package org.apache.jena.shex.reporting;


import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.validation.ReportInfo;
import org.apache.jena.shex.validation.ShexReportElementRemove;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Result of validating a node against a {@link ShapeDecl} or a {@link Shape}.
 * Is a hierarchic structure.
 */
public abstract class NodeSatExprReport extends SimpleReportElement {

    // TODO to be replaced by a factory method
    abstract public NodeSatExprReport create(Node node, ShapeExpr shapeExpr);

    private final Node node;
    private final ShapeExpr expr;
    private List<ReportElement> infos = new ArrayList<>();

    // TODO remove parent, does not seem necessary
    private NodeSatExprReport parent = null;
    private final List<NodeSatExprReport> children = new ArrayList<>();

    protected NodeSatExprReport(Node node, ShapeExpr expr, NodeSatExprReport parent) {
        if (! (expr instanceof NodeConstraint || expr instanceof Shape || expr instanceof ShapeExprRef))
            throw new IllegalArgumentException("Expression must be an atomic shape expression (node constraint, shape, or reference) or null.");
        this.node = node;
        this.expr = expr;
        this.parent = parent;
    }

    // TODO quick fix for factory and Start SemActs, to remove
    protected NodeSatExprReport() {
        super(null, null);
        // TODO remove
        this.node = null;
        this.expr = null;
    }

    public void setParent (NodeSatExprReport parent) {
        if (this.parent != null)
            throw new IllegalStateException("Can't set parent twice");
        this.parent = parent;
    }

    public abstract NodeSatExprReport createChild(Node node, ShapeExpr expr);

    // TODO add message
    public void setSatisfies(boolean satisfies) {
        setStatus(satisfies ? ShexStatus.conformant : ShexStatus.nonconformant);
    }

    public void addInfoSuccess(String message) {
        setMessage(message);
    }

    public void addInfoFailure(NodeConstraintComponent subExpr, String message) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void addInfoFailure(Expression subExpr, Set<Triple> subNeigh, String message) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void addInfoFailure(Expression subExpr, String message) {
        addInfoFailure(subExpr, null, message);
    }

    public List<NodeSatExprReport> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public NodeSatExprReport getParent() {
        return parent;
    }

    public Node getNode() {
        return node;
    }

    public ShapeExpr getShapeExpr() {
        return expr;
    }

    public NodeSatExprReport getReference() {

        return new NodeSatExprReport() {

            private NodeSatExprReport parent = null;
            private final NodeSatExprReport report = NodeSatExprReport.this;

            @Override
            public void setParent(NodeSatExprReport parent) {
                if (this.parent != null) {
                    throw new IllegalStateException("Can't set parent twice");
                }
                this.parent = parent;
            }

            @Override
            public NodeSatExprReport createChild(Node node, ShapeExpr expr) {
                throw new UnsupportedOperationException("not yet implemented");
            }

            @Override
            public void setSatisfies(boolean satisfies) {
                throw new UnsupportedOperationException();
            }

            public void addInfoSuccess(String message, Expression subExpr, Set<Triple> subNeighbourhood) {
                throw new UnsupportedOperationException();
            }

            public void addInfoFailure(String errorMessage, Expression expr, Set<Triple> subNeighbourhood) {
                throw new UnsupportedOperationException();
            }

            public NodeSatExprReport create(Node node, ShapeExpr shapeExpr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Node getNode() {
                return report.getNode();
            }

            @Override
            public ShapeExpr getShapeExpr() {
                return report.getShapeExpr();
            }

            @Override
            public ShexStatus getStatus() {
                return report.getStatus();
            }



            @Override
            public NodeSatExprReport getParent() {
                return parent;
            }

            @Override
            public List<NodeSatExprReport> getChildren() {
                return report.getChildren();
            }

        };
    }

    @Override
    public String toString() {
        return toString(this, 2);
    }



    private static String toString(NodeSatExprReport r, int indent) {
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
        for (NodeSatExprReport child : r.children) {
            sb.append(" ".repeat(indent));
            // TODO indentation
            sb.append(String.format("- %s", NodeSatExprReport.toString((NodeSatExprReport) child, indent+2)));
        }
        return sb.toString();
    }



}
