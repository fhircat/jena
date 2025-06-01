package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;

import java.util.Set;

public class ExhaustiveReporter implements Reporter {

    private static boolean DEBUG = true;  // TODO remove

    private static ExhaustiveReporter factoryInstance = new ExhaustiveReporter();
    public static ExhaustiveReporter factory() {
        return factoryInstance;
    }

    private ExpressionHReport report;

    private ExhaustiveReporter() {}

    private ExhaustiveReporter(Node node, Expression expr, Set<Triple> neigh) {
        report = new ExpressionHReport(node, expr, neigh);
    }

    @Override
    public ExhaustiveReporter createRoot(Node node, Expression expr) {
        return new ExhaustiveReporter(node, expr, null);
    }

    @Override
    public ExhaustiveReporter createChild(Node node, Expression expr, Set<Triple> neigh) {
        ExhaustiveReporter r = new ExhaustiveReporter(node, expr, neigh);
        report.addChild(r.report);
        return r;
    }

    @Override
    public void setResult(ShexStatus status, String message, Object details) {
        if (DEBUG && report.getStatus() != null)
            throw new IllegalStateException("Result set twice");
        report.setStatus(status);
        report.setMessage(message);
    }

    @Override
    public void addInfo(ShexStatus status, String message, Object details) {
        report.addInfo(new SimpleReportElement(status, message));
    }

    @Override
    public ReportElement getReport() {
        return report;
    }

    @Override
    public boolean isValidateOnly () { return false; }
}
