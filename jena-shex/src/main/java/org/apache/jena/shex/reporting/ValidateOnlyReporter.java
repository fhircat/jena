package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.ShapeExprRef;

import java.util.Set;

public class ValidateOnlyReporter implements Reporter {

    private Report report;

    public static ValidateOnlyReporter factory() {
        return new ValidateOnlyReporter();
    }

    @Override
    public Reporter createRoot(Node node, ShapeExprRef expr) {
        return new ValidateOnlyReporter();
    }

    @Override
    public Reporter createChild(Node node, Expression expr, Set<Triple> neigh) {
        return new EmptyReporter();
    }

    @Override
    public void setReferenceTo(Report report, String additionalMessage) {
        // TODO needs to be tested
        this.report = report;
    }

    @Override
    public void setResult(ShexStatus status) {
        if (SimpleExhaustiveReporter.DEBUG && report != null)
            throw new IllegalStateException("Result has already been set.");
        report = new SimpleReport(status, "");
    }

    @Override
    public void addInfo(String message, Object details) {
        // empty
    }

    @Override
    public Report getReport() {
        return report;
    }

    @Override
    public boolean isValidateOnly() {return true;}

    private static class EmptyReporter implements Reporter {

        @Override
        public Reporter createRoot(Node node, ShapeExprRef expr) {
            return this;
        }

        @Override
        public Reporter createChild(Node node, Expression expr, Set<Triple> neigh) {
            return this;
        }

        @Override
        public void setReferenceTo(Report report, String additionalMessage) {
            // empty
        }

        @Override
        public void setResult(ShexStatus status) {
            // empty
        }

        @Override
        public void addInfo(String message, Object details) {
            // empty
        }

        @Override
        public Report getReport() {
            return null;
        }

        @Override
        public final boolean isValidateOnly() { return true; }
    }
}
