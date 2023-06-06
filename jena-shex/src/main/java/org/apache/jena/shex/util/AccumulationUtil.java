package org.apache.jena.shex.util;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.expressions.TripleConstraint;
import org.apache.jena.shex.expressions.TripleExpr;
import org.apache.jena.shex.expressions.VoidWalker;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class AccumulationUtil {

    /** Collect the forward and backward predicates of a triple expression.
     * Follows triple expression references.
     * @param tripleExpr The triple expression to explore
     * @param tripleRefsDefs Retrieves the definition of a triple expression reference
     * @return A pair of sets (forward predicates, inverse predicates)
     */
    public static Pair<Set<Node>, Set<Node>> collectPredicates (TripleExpr tripleExpr,
                                                                Function<Node, TripleExpr> tripleRefsDefs) {

        Set<Node> fwdPredicates = new HashSet<>();
        Set<Node> invPredicates = new HashSet<>();
        TripleExprAccumulationVisitor<Node> fwdPredAccumulator = new TripleExprAccumulationVisitor<>(fwdPredicates) {
            @Override
            public void visit(TripleConstraint tripleConstraint) {
                if (! tripleConstraint.isInverse())
                    accumulate(tripleConstraint.getPredicate());
            }
        };
        TripleExprAccumulationVisitor<Node> invPredaccumulator = new TripleExprAccumulationVisitor<>(invPredicates) {
            @Override
            public void visit(TripleConstraint tripleConstraint) {
                if (tripleConstraint.isInverse())
                    accumulate(tripleConstraint.getPredicate());
                super.visit(tripleConstraint);
            }
        };

        VoidWalker walker = VoidWalker.builder()
                .processTripleExprsWith(fwdPredAccumulator)
                .processTripleExprsWith(invPredaccumulator)
                .followTripleExprRefs(tripleRefsDefs)
                .build();
        tripleExpr.visit(walker);
        return new ImmutablePair<>(fwdPredicates, invPredicates);
    }

}
