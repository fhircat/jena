package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;

import java.util.Set;

public class SimpleExhaustiveReporter implements Reporter {

    public static final boolean DEBUG = true;  // TODO remove

    private static SimpleExhaustiveReporter factoryInstance = new SimpleExhaustiveReporter();
    public static SimpleExhaustiveReporter factory() {
        return factoryInstance;
    }

    protected ExpressionHReport report;

    protected SimpleExhaustiveReporter() {}

    protected SimpleExhaustiveReporter(Node node, Expression expr, Set<Triple> neigh) {
        report = new ExpressionHReport(node, expr, neigh);
    }

    @Override
    public SimpleExhaustiveReporter createRoot(Node node, Expression expr) {
        return new SimpleExhaustiveReporter(node, expr, null);
    }

    @Override
    public SimpleExhaustiveReporter createChild(Node node, Expression expr, Set<Triple> neigh) {
        SimpleExhaustiveReporter r = new SimpleExhaustiveReporter(node, expr, neigh);
        report.addChild(r.report);
        return r;
    }

    @Override
    public void setReferenceTo(Report r, String additionalMessage) {
        addInfo(additionalMessage, null, r.getStatus() == ShexStatus.conformant);
        this.report.asReferenceTo(r, additionalMessage);
    }

    /*
    @Override
    public void addChild(Node node, Expression expr, Set<Triple> neigh, Reporter child) {
        if (! (child instanceof ExhaustiveReporter))
            throw new IllegalArgumentException("Incompatible type for child. Should be " + this.getClass() + ".");
        ExhaustiveReporter r = (ExhaustiveReporter)child;
        if (r.report.getNode() != node || r.report.getExpr() != expr || r.report.getSubNeigh() != neigh)
            throw new IllegalArgumentException("Different node or expression or neighborhood");
        report.addChild(((ExhaustiveReporter)child).getReport());
    }
    */

    @Override
    public void setResult(ShexStatus status) {
        if (DEBUG && report.getStatus() != null)
            throw new IllegalStateException("Result set twice");
        report.setStatus(status);
    }

    @Override
    public void addInfo(String message, Object details, boolean isConformant) {
        report.addInfo(new ReportInfo(message, details, isConformant));
    }

    @Override
    public ExpressionHReport getReport() {
        return report;
    }

    @Override
    public boolean isValidateOnly () { return false; }

}
