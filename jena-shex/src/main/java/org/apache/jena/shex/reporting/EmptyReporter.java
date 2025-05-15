package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;

import java.util.Set;

public class EmptyReporter implements Reporter {

    private boolean isValid;

    @Override
    public Reporter createNew(Node node, Node label) {
        // empty
        return new EmptyReporter();
    }

    @Override
    public void setFinalResult(boolean isValid) {
        this.isValid = isValid;
    }

    @Override
    public ReportElement getReport() {
        if (isValid)
            return new SimpleReportElement("OK", ShexStatus.conformant);
        else
            return new SimpleReportElement("ERROR", ShexStatus.nonconformant);
    }

    @Override
    public void addChild(Node n1, Expression e1, Node n2, Expression e2, Set<Triple> n2subNeigh) {
        // empty
    }

    @Override
    public void setResult(Node node, Expression expr, Set<Triple> subNeigh, ShexStatus status, String message, Object details) {
        // empty
    }

    @Override
    public void setInvalid(Node node, NodeConstraintComponent e, String m) {
        // empty
    }

    @Override
    public void setResult(Node node, ShapeExpr expr, SemAct semAct, ShexStatus status) {
        // empty
    }

    @Override
    public void setResult(Set<Triple> triples, TripleExpr expr, SemAct semAct, ShexStatus status) {
        // empty
    }

    @Override
    public void setResult(ShexSchema schema, SemAct semAct, ShexStatus status) {
        // empty
    }

    @Override
    public void setResult(ShexStatus status, String message) {
        // empty
    }
}
