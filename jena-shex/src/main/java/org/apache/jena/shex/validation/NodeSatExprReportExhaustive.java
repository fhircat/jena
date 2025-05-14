package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.reporting.NodeSatExprReport;

import java.util.Set;

/**
 * The result of validating a node against a {@link ShapeDecl} or {@link Shape}.
 */
public class NodeSatExprReportExhaustive extends NodeSatExprReport {

    // TODO make a real factory
    public static NodeSatExprReportExhaustive factory() {
        return new NodeSatExprReportExhaustive();
    }

    private NodeSatExprReportExhaustive(){}

    public NodeSatExprReportExhaustive create (Node node, ShapeExpr expr) {
        return new NodeSatExprReportExhaustive(node, expr, null);
    }

    public static NodeSatExprReportExhaustive create(Node node, ShapeDecl shapeDecl) {
        return new NodeSatExprReportExhaustive(node, ShapeExprRef.create(shapeDecl.getLabel()), null);
    }

    protected NodeSatExprReportExhaustive(Node node, ShapeExpr expr, NodeSatExprReport parent) {
        super(node, expr, parent);
    }


    @Override
    public NodeSatExprReport createChild(Node node, ShapeExpr expr) {
        return new NodeSatExprReportExhaustive(node, expr, this);
    }
}
