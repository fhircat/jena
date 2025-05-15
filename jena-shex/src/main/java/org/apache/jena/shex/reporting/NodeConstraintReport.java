package org.apache.jena.shex.reporting;

import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.NodeConstraintComponent;

public class NodeConstraintReport extends SimpleReportElement {

    private NodeConstraintComponent constraint;

    public NodeConstraintReport(NodeConstraintComponent constraint, ShexStatus status, String message) {
        setMessage(message);
        setStatus(status);
        this.constraint = constraint;
    }
}
