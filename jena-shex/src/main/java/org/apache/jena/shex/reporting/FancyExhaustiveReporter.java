package org.apache.jena.shex.reporting;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.calc.TripleExprAccumulationVisitor;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.validation.TripleExprForValidation;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private Map<Node, TripleExprForValidation> shapeTripleExpressions = null;
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

    @Override
    public void informShapeTripleExpressions (Map<Node, TripleExprForValidation> expressions) {
        this.shapeTripleExpressions = expressions;
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
                    && isDeterministic(shapeTripleExpressions)) {
                addErrorMessageShapeUnmatched();
            } else if (report.expr instanceof ShapeAnd) {
                addInfo(false,"One of the conjuncts of ShapeAnd was not satisfied.", null);
            } else if (report.expr instanceof ShapeOr) {
                addInfo(false, "None of the disjuncts of ShapeOr was satisfied.", null);
            } else if (report.expr instanceof ShapeNot) {
                addInfo(false, "A negated shape expression was satisfied.", null);
            }
        }
        return super.setIsConformant(isConformant);
    }

    /** All the expressions are natively SORBE and if there are repeated predicates among all the expressions, then
     * we can statically establish that every triple could match at most one triple constraint. */
    private boolean isDeterministic(Map<Node, TripleExprForValidation> expressions) {
        if (! expressions.values().stream().allMatch(TripleExprForValidation::isNativeSorbe))
            return false;
        Map<Node, List<TripleConstraint>> repeatedPredicates = expressions.values()
                .stream()
                .flatMap(e -> e.getTripleConstraints().stream())
                .collect(Collectors.groupingBy(
                        TripleConstraint::getPredicate,
                        Collectors.mapping(t->t, Collectors.toList())));
        repeatedPredicates.entrySet().removeIf(e -> e.getValue().size() <= 1);
        // TODO a more complete version which looks at the values of triple constraints
        return repeatedPredicates.isEmpty();
    }

    private void addErrorMessageShapeUnmatched() {
        reportSimpleCardinalityErrors();
    }

    /** A simple cardinality error is about triple constraints that have a directly attached cardinality (or default 1)
     * and the number of triples that matched is incorrect.
     * Reports the error, and returns whether some error was found or reported. */
    private boolean reportSimpleCardinalityErrors() {
        AtomicBoolean hasError = new AtomicBoolean(false);
        Map<TripleConstraint, List<Triple>> invertedMatching = lastNonConformantMatching.entrySet()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,                   // group by value
                        Collectors.mapping(Map.Entry::getKey,  // collect keys
                                Collectors.toList())
                ));
        Map<TripleConstraint, Cardinality> cards = cardinalities(shapeTripleExpressions.values());
        cards.forEach((tc, card) -> {
            int nb = invertedMatching.getOrDefault(tc, Collections.emptyList()).size();
            if (nb < card.min || nb > card.max) {
                String m = String.format(
                        "Cardinality error. Incorrect number of triples matched a triple expression. Expected: between %d and %d; found: %d",
                        card.min, card.max, nb);
                addInfo(false, m, Pair.of(tc, invertedMatching.get(tc)));
                hasError.set(true);
            }
        });
        return hasError.get();
    }


    /** The cardinality directly on the triple constraint (or default 1 cardinality), or null if this is not the case. */
    private static Map<TripleConstraint, Cardinality> cardinalities (Collection<TripleExprForValidation> expressions) {
        List<Pair<TripleConstraint, Cardinality>> cardMap = new ArrayList<>(1);
        TripleExprAccumulationVisitor<Pair<TripleConstraint, Cardinality>> cardinalityFinder =
                new TripleExprAccumulationVisitor<>(cardMap) {
            @Override
            public void visit(TripleExprCardinality te) {
                if (te.getSubExpr() instanceof TripleConstraint)
                    accumulate(Pair.of((TripleConstraint) te.getSubExpr(), te.getCardinality()));
            }

            @Override
            public void visit(TripleConstraint tc) {
                accumulate(Pair.of(tc, Cardinality.ONE));
            }
        };
        for (TripleExprForValidation expr : expressions)
            expr.getOriginalExpr().visit(cardinalityFinder);
        return cardMap.stream()
                .collect(Collectors.toMap(
                        Pair::getKey,
                        Pair::getValue,
                        (c1, c2) -> c1 == Cardinality.ONE ? c2 : c1
                ));
    }


}
