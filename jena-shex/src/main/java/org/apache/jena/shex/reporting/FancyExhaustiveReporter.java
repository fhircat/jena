package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FancyExhaustiveReporter extends SimpleExhaustiveReporter {

    private static final FancyExhaustiveReporter factoryInstance = new FancyExhaustiveReporter();
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
    public FancyExhaustiveReporter createRoot(Node node, ShapeExprRef expr) {
        return new FancyExhaustiveReporter(node, expr, null);
    }

    @Override
    public FancyExhaustiveReporter createChild(Node node, Expression expr, Set<Triple> neigh) {
        FancyExhaustiveReporter r = new FancyExhaustiveReporter(node, expr, neigh);
        report.addChild(r.report);
        return r;
    }

    // -----------------------------------------------------------
    // Attributes for reporting errors in TripleExpr
    // -----------------------------------------------------------
    private Map<Triple, List<TripleConstraint>> predicateBasedPreMatching = null;
    private Map<Triple, List<TripleConstraint>> preMatching = null;
    private Map<Triple, TripleConstraint> lastNonConformantMatching = null;
    private Reporter.MatchingNotSatisfiedReason lastNonConformantMatchingReason = null;

    @Override
    public void informPredicateBasedPreMatching(Map<Triple, List<TripleConstraint>> predicateBasedPreMatching) {
        this.predicateBasedPreMatching = new HashMap<>(predicateBasedPreMatching);
    }

    @Override
    public void informPreMatching(Map<Triple, List<TripleConstraint>> cleanPreMatching) {
        this.preMatching = new HashMap<>(cleanPreMatching);
    }

    @Override
    public void informCandidateMatchingConformance(boolean isConformant, Map<Triple, TripleConstraint> matching,
                                                   Reporter.MatchingNotSatisfiedReason reasonIfNonConformant) {
        if (! isConformant) {
            this.lastNonConformantMatching = matching;
            this.lastNonConformantMatchingReason = reasonIfNonConformant;
        }
        super.informCandidateMatchingConformance(isConformant, matching, reasonIfNonConformant);
    }

    // -----------------------------------------------------------------------------
    // Generate the error message
    // -----------------------------------------------------------------------------
    @Override
    public boolean setIsConformant (boolean isConformant) {
        if (! isConformant) {
            if (report.expr instanceof Shape
                    // Conditions fancy error reporting for shapes
                    && lastNonConformantMatchingReason == MatchingNotSatisfiedReason.TRIPLE_EXPRS
                    && isDeterministic((Shape) report.expr)
                    && isSorbe((Shape) report.expr)) {
                addErrorMessageShapeUnmatched();
            }
        }
        return super.setIsConformant(isConformant);
    }

    private boolean isDeterministic(Shape shape) {
        // TODO
        return true;
    }

    private boolean isSorbe(Shape shape) {
        // TODO
        return true;
    }

    private void addErrorMessageShapeUnmatched() {
        Shape shape = (Shape) report.expr;
        // Is this a cardinality error ?
        Map<TripleConstraint, List<Triple>> invertedMatching = lastNonConformantMatching.entrySet()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,                   // group by value
                        Collectors.mapping(Map.Entry::getKey,  // collect keys
                                Collectors.toList())
                ));
        TripleConstraint constraint = null;
        /*
        invertedMatching.entrySet().stream()
                .filter(e -> e.getValue().size() )
        */
    }

}
