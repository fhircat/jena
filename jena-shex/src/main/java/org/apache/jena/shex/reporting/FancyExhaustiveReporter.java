package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.TripleConstraint;
import org.apache.jena.shex.expressions.TripleExpr;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class FancyExhaustiveReporter extends SimpleExhaustiveReporter {

    private static FancyExhaustiveReporter factoryInstance = new FancyExhaustiveReporter();
    public static FancyExhaustiveReporter factory() {
        return factoryInstance;
    }
    private FancyExhaustiveReporter() {
        super();
    }

    protected FancyExhaustiveReporter(Node node, Expression expr, Set<Triple> neigh) {
        report = new ExpressionHReport(node, expr, neigh);
    }

    @Override
    public FancyExhaustiveReporter createRoot(Node node, Expression expr) {
        return new FancyExhaustiveReporter(node, expr, null);
    }

    @Override
    public FancyExhaustiveReporter createChild(Node node, Expression expr, Set<Triple> neigh) {
        FancyExhaustiveReporter r = new FancyExhaustiveReporter(node, expr, neigh);
        report.addChild(r.report);
        return r;
    }

    @Override
    public void informMatchableTriples(Map<Triple, List<TripleConstraint>> predicateBasedPreMatching) {
        super.informMatchableTriples(predicateBasedPreMatching);
    }

    @Override
    public void informMatchedTriples(Map<Triple, List<TripleConstraint>> cleanPreMatching) {
        super.informMatchedTriples(cleanPreMatching);
    }

    @Override
    public void informUnmatchedTriples(Set<Triple> unmatchedTriples) {
        super.informUnmatchedTriples(unmatchedTriples);
    }

    @Override
    public void informCandidateMatching(Map<Triple, TripleConstraint> matching) {
        super.informCandidateMatching(matching);
    }

    @Override
    public void informCandidateMatchingFailedForTripleExpression(Map<Triple, TripleConstraint> matching, TripleExpr tripleExpr) {
        super.informCandidateMatchingFailedForTripleExpression(matching, tripleExpr);
    }
}
