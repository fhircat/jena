package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;

import java.util.Set;

public class ValidateOnlyReporter implements Reporter {

    private ReportElement report;

    public static ValidateOnlyReporter factory() {
        return new ValidateOnlyReporter();
    }

    @Override
    public Reporter createRoot(Node node, Expression expr) {
        return new ValidateOnlyReporter();
    }

    @Override
    public Reporter createChild(Node node, Expression expr, Set<Triple> neigh) {
        return new EmptyReporter();
    }

    @Override
    public void setResult(ShexStatus status, String message, Object details) {
        report = new SimpleReportElement(status, message);
    }

    @Override
    public void addInfo(ShexStatus status, String message, Object details) {
        // empty
    }

    @Override
    public ReportElement getReport() {
        return report;
    }

    @Override
    public boolean isValidateOnly() {return true;}

    private class EmptyReporter implements Reporter {

        @Override
        public Reporter createRoot(Node node, Expression expr) {
            return this;
        }

        @Override
        public Reporter createChild(Node node, Expression expr, Set<Triple> neigh) {
            return this;
        }

        @Override
        public void setResult(ShexStatus status, String message, Object details) {
            // empty
        }

        @Override
        public void addInfo(ShexStatus status, String message, Object details) {
            // empty
        }

        @Override
        public ReportElement getReport() {
            return null;
        }

        @Override
        public final boolean isValidateOnly() { return true; }
    }
}
