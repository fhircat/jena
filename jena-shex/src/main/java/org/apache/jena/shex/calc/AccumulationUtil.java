package org.apache.jena.shex.calc;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.expressions.*;

import java.util.Collection;
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
        TripleExprAccumulationVisitor<Node> invPredAccumulator = new TripleExprAccumulationVisitor<>(invPredicates) {
            @Override
            public void visit(TripleConstraint tripleConstraint) {
                if (tripleConstraint.isInverse())
                    accumulate(tripleConstraint.getPredicate());
                super.visit(tripleConstraint);
            }
        };

        ExpressionWalker walker = ExpressionWalker.builder()
                .processTripleExprsWith(fwdPredAccumulator)
                .processTripleExprsWith(invPredAccumulator)
                .followTripleExprRefs(tripleRefsDefs)
                .build();
        tripleExpr.visit(walker);
        return new ImmutablePair<>(fwdPredicates, invPredicates);
    }

    /** Accumulates the shape expression references that appear in a shape expression.
     * Explores recursively the shape expressions that appear as value expressions in triple constraints.
     * @param shapeExpr The shape expression to explore
     * @param acc The collection to which the references are added
     */
    public static void accumulateAllShapeExprRefsInShapeExpr (ShapeExpr shapeExpr, Collection<Node> acc) {
        ShapeExprAccumulationVisitor<Node> seAccVisitor = new ShapeExprRefAccumulationVisitor(acc);
        ExpressionWalker walker = ExpressionWalker.builder()
                .processShapeExprsWith(seAccVisitor)
                .traverseTripleConstraints()
                .traverseShapes()
                .build();
        shapeExpr.visit(walker);
    }

    /** Accumulates the triple expression references that appear in a shape expression.
     * Explores recursively the shape expressions that appear as value expressions in triple constraints.
     * @param shapeExpr The shape expression to explore
     * @param acc The collection to which the references are added
     */
    public static void accumulateAllTripleExprRefsInShapeExpr(ShapeExpr shapeExpr, Collection<Node> acc) {
        TripleExprRefAccumulationVisitor teAccVisitor = new TripleExprRefAccumulationVisitor(acc);
        ExpressionWalker walker = ExpressionWalker.builder()
                .processTripleExprsWith(teAccVisitor)
                .traverseShapes()
                .traverseTripleConstraints()
                .build();
        shapeExpr.visit(walker);
    }


    /** Accumulates the direct shape expression references that appear in a shape expression.
     * Does not traverse shapes, that is, does not explore recursively shape expressions that appear as value expressions in triple constraints
     * @param shapeExpr The shape expression to explore
     * @param acc The collection to which the references are added
     */
    public static void accumulateDirectShapeExprRefsInShapeExpr (ShapeExpr shapeExpr, Collection<Node> acc) {
        ShapeExprAccumulationVisitor<Node> seAccVisitor = new ShapeExprRefAccumulationVisitor(acc);
        ExpressionWalker walker = ExpressionWalker.builder()
                .processShapeExprsWith(seAccVisitor)
                .build();
        shapeExpr.visit(walker);
    }

    /** Accumulates direct the triple expression references that appear in a triple expression.
     * Does not traverse value expressions in triple constraints.
     * @param tripleExpr The shape expression to explore
     * @param acc Accumulation collection
     */
    public static void accumulateDirectTripleExprRefsInTripleExpr (TripleExpr tripleExpr, Collection<Node> acc) {
        TripleExprAccumulationVisitor<Node> teAccVisitor = new TripleExprRefAccumulationVisitor(acc);
        ExpressionWalker walker = ExpressionWalker.builder()
                .processTripleExprsWith(teAccVisitor)
                .build();
        tripleExpr.visit(walker);
    }


    // ----------------------------------------------------------------------------------------------
    // Visitors
    // ----------------------------------------------------------------------------------------------

    private static class ShapeExprRefAccumulationVisitor extends ShapeExprAccumulationVisitor<Node> {
        public ShapeExprRefAccumulationVisitor(Collection<Node> acc) {
            super(acc);
        }
        @Override
        public void visit(ShapeExprRef shapeExprRef) {
            accumulate(shapeExprRef.getLabel());
        }
    }

    private static class TripleExprRefAccumulationVisitor extends TripleExprAccumulationVisitor<Node> {
        public TripleExprRefAccumulationVisitor(Collection<Node> acc) {
            super(acc);
        }
        @Override
        public void visit(TripleExprRef tripleExprRef) {
            accumulate(tripleExprRef.getLabel());
        }
    }


}
