/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.shex.validation;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.calc.AccumulationUtil;
import org.apache.jena.shex.calc.ExpressionWalker;
import org.apache.jena.shex.calc.TripleExprAccumulationVisitor;
import org.apache.jena.shex.calc.TypedTripleExprVisitor;
import org.apache.jena.shex.expressions.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Encapsulates a {@link TripleExpr} together with useful static analysis information for speeding up validation.
 * In particular, stores a SORBE version of the expression if it is not SORBE.
 * SORBE triple expressions are associated with an efficient validation algorithm. They are explained below.
 *
 * A {@link TripleExprForValidation} is created using the {@link #create(TripleExpr, ShexSchema)} method.
 * The <pre>create</pre> method should not be used directly but only through
 * {@link ValidationContext#getExprForValidation(TripleExpr)} which will memoize the already computed triple expressions.
 * Each {@link TripleExprForValidation} stores the expression given in the create method, called the <em>original</em> expression.
 * If the original expression is not SORBE, then the <pre>create</pre> method computes and stores its equivalent
 * SORBE form {@link #sorbeForm}.
 * The SORBE form has a different set of atomic triple constraints, which are going to be used in the matchings.
 * Methods allow to return back to the triple constraints of the original expression // TODO document after cleaning.
 *
 * <p>
 * A SORBE triple expression is a triple expression that satisfies:
 * <ul>
 *     <li>it does not contain triple expression references,</li>
 *     <li>cardinalities other than ?, *, + appear only on triple constraints,</li>
 *     <li>cardinality + appears only on sub-expressions that cannot be satisfied by an empty neighbourhood.</li>
 * </ul>
 *
 * <p>
 * Given a triple expression, called <em>origin</em>, one can construct an equivalent SORBE triple expression.
 * This involves replacing triple expression references by their definition, and copying some of the subexpressions.
 * For instance, if <pre>tc1, tc2</pre> are triple constraints, then the triple expression <pre>(tc1 | tc2) {2;4}</pre>
 * is not SORBE, but it is equivalent to the SORBE triple expression <pre>(tc1 | tc2) ; (tc1 | tc2) ; (tc1 | tc2)? ; (tc1 | tc2)?</pre>
 * <p>
 * When copying occurs, all occurrences of the triple constraint <pre>tc1</pre> in the SORBE triple expression <em>originate</em> from the triple constraint <pre>tc1</pre> from the origin expression.
 * Given a sub-expression of the <em>origin</em> triple expression, we can retrieve all triple constraints.
 * This origin information is used to determine which triples matched which triple constraint from <em>origin</em>.
 *
 */
/*package*/ class TripleExprForValidation {

    /** The encapsulated triple expression. */
    private final TripleExpr expr;

    /** If the expression is not SORBE, this is its SORBE equivalent expression. Is null otherwise. */
    private final TripleExpr sorbeForm;

    /** If the expression is not SORBE (ie {@link #sorbeForm} is not null),
     * associates with each original triple constraint the list of its copies in the sorbe expr.
     * Is null if {{@link #sorbeForm}} is null. */
    private final EMap<TripleConstraint, List<TripleConstraint>> tripleConstraintCopiesMap;

    /** The list of sub-expressions of the original expression that have associated semantic actions.
     * Needed for evaluating semantic actions. */
    private final List<TripleExpr> subExprsWithSemActs;

    private TripleExprForValidation(TripleExpr expr, TripleExpr sorbeForm,
                                    List<TripleExpr> subExprsWithSemActs,
                                    EMap<TripleConstraint, List<TripleConstraint>> tripleConstraintCopiesMap) {
        this.expr = expr;
        this.sorbeForm = sorbeForm;
        this.subExprsWithSemActs = subExprsWithSemActs;
        this.tripleConstraintCopiesMap = tripleConstraintCopiesMap;
    }

    // ------------------------------------------------------------------------
    // Package accessible methods
    // ------------------------------------------------------------------------

    /*package*/ static TripleExprForValidation create(TripleExpr tripleExpr, ShexSchema schema) {

        List<TripleExpr> subExprsWithSemActs
                = AccumulationUtil.collectSubExprsWithSemActs(tripleExpr, schema::getTripleExpr);

        if (computeIsSorbe(tripleExpr))
            return new TripleExprForValidation(tripleExpr, null, subExprsWithSemActs, null);

        EMap<TripleConstraint, List<TripleConstraint>> tripleConstraintCopiesMap = new EMap<>();
        SorbeConstructor constructor = new SorbeConstructor(tripleConstraintCopiesMap, schema);
        TripleExpr sorbe = tripleExpr.visit(constructor);
        return new TripleExprForValidation(tripleExpr, sorbe, subExprsWithSemActs, tripleConstraintCopiesMap);
    }

    /** With every triple in the input, associates the triple constraints of this triple expression that have the same
     * predicate as the triple.
     * Uses the triple constraints of the original expression if it is SORBE, or its {@link #sorbeForm} otherwise. */
    /*package*/ Map<Triple, List<TripleConstraint>> getPredicateBasedPreMatching(Collection<Triple> triples) {
        Map<Node, List<TripleConstraint>> tcsByPredicate = getTripleConstraintsGroupedByPredicate();
        return triples.stream()
                .filter(t -> tcsByPredicate.containsKey(t.getPredicate()))
                .collect(Collectors.toMap(Function.identity(),
                        t -> new ArrayList<>(tcsByPredicate.get(t.getPredicate()))));
    }

    /** Checks whether the matching is valid for this triple expression.
     * Uses the original expression if it is SORBE, or its {@link #sorbeForm} otherwise. */
    /*package*/ boolean isValid(Map<Triple, TripleConstraint> matching) {
        Bag bag = Bag.fromMatching(matching, getAllTripleConstraints());
        Cardinality interval =  getRelevantExpression().visit(new IntervalComputation(this, bag));
        return interval.min <= 1 && 1 <= interval.max;
    }

    /** Checks whether the bag has value 0 for every triple constraint that is part of the sub-expression.
     * Used in {@link IntervalComputation}.
     * The sub-expression can be from the original expression, or from its SORBE form, as long as it is consistent
     * with the triple constraints that occur in the bag. */
    /*package*/ boolean isEmptySubbag(Bag bag, TripleExpr subExpr) {
        return getTripleConstraintsOfSubExpr(subExpr).stream()
                .allMatch(tc -> bag.getCard(tc) == 0);
    }

    /** Retrieves the triples matched to sub-expressions that have semantic actions.
     * Needed for evaluating semantic actions.
     * The triple expressions of the result are always those from the original triple expression, even when
     * it is not SORBE.
     * The matching given in input uses the triple constraints of the SORBE form whenever it exists. */
    /*package*/ List<Pair<TripleExpr, Set<Triple>>> getSemActsSubExprsAndTheirMatchedTriples(
            Map<Triple, TripleConstraint> matching, ValidationContext vCxt) {
        // Cannot return a map here because two triple expressions can be equal (wrt Object#equals) but distinct in the AST

        return subExprsWithSemActs.stream()
                .map(originSubExpr -> new ImmutablePair<>(originSubExpr,
                        triplesMatchedInOriginSubExpr(matching, originSubExpr, vCxt)))
                .collect(Collectors.toList());
    }

    /** The triple constraints of this triple expression. Memorized.
     * Uses the original expression if it is SORBE, or its {@link #sorbeForm} otherwise. */
    /*package*/ Set<TripleConstraint> getTripleConstraints() {
        return getTripleConstraintsOfSubExpr(getRelevantExpression());
    }

    // ------------------------------------------------------------------------
    // Unclassified : these seem to be used for reporting or for debugging
    // ------------------------------------------------------------------------
    // TODO which of these are still useful after reporter is written (also whether public or private)

    /** Returns true if the original expression is already SORBE */
    boolean isNativeSorbe() {
        return sorbeForm == null;
    }

    /* package */ TripleExpr getOriginTripleExpr() { return expr; }

    /** The triples that are matched with an origin sub-expression.
     *
     * @param matching Matching to origin triple constraints if SORBE, to {@link #sorbeForm} triple constraints otherwise.
     * @param originSubExpr Origin sub-expression
     * @param vCxt
     * @return The set of triples that {@code matching} matches to some sorbe triple constraint which origin is in {@code originSubExpr}
     */
    /* package */ Set<Triple> triplesMatchedInOriginSubExpr(Map<Triple, TripleConstraint> matching,
                                                            TripleExpr originSubExpr,
                                                            ValidationContext vCxt) {

        ESet<TripleConstraint> tripleConstraints = getTripleConstraintsOfOriginSubExpr(originSubExpr, vCxt);
        return matching.entrySet().stream()
                .filter(e -> tripleConstraints.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }



    // ---------------------------------------------------------------------------------------------------------
    // Accessors and memoized information
    // ---------------------------------------------------------------------------------------------------------

    private TripleExpr getRelevantExpression() {
        return this.sorbeForm != null ? this.sorbeForm : this.expr;
    }

    /** The triple constraints of this triple expression. Memorized.
     * Uses the original expression if it is SORBE, or its {@link #sorbeForm} otherwise. */
    private List<TripleConstraint> getAllTripleConstraints() {
        if (allSorbeTripleConstraints == null) {
            allSorbeTripleConstraints = new ArrayList<>();
            AccumulationUtil.accumulateDirectTripleConstraints(getRelevantExpression(), allSorbeTripleConstraints);
        }
        return allSorbeTripleConstraints;
    }
    private List<TripleConstraint> allSorbeTripleConstraints;

    /** The triple constraints of this expression which origins are in the given origin sub-expression.
     * Memorized.
     * The returned triple constraints are from the original expression if it is SORBE, or of its {@link #sorbeForm}
     * otherwise.
     * Useful for semantic actions evaluation.
     * @param originSubExpr Must be a sub-expression of the original triple expression.
     * @param vCxt
     * @return
     */
    private ESet<TripleConstraint> getTripleConstraintsOfOriginSubExpr(TripleExpr originSubExpr, ValidationContext vCxt) {
        return srcSubExprToItsTripleConstraintsMap.computeIfAbsent(originSubExpr, e -> {
            List<TripleConstraint> sourceTripleConstraints = new ArrayList<>();
            AccumulationUtil.accumulateTripleConstraintsFollowTripleExprReferences(originSubExpr,
                    vCxt::getTripleExpr, sourceTripleConstraints);
            if (sorbeForm == null)
                return sourceTripleConstraints.stream()
                        .collect(ESet.collector());
            else
                return sourceTripleConstraints.stream()
                        .flatMap(tc -> tripleConstraintCopiesMap.get(tc).stream())
                        .collect(ESet.collector());
        });
    }
    private final EMap<TripleExpr, ESet<TripleConstraint>> srcSubExprToItsTripleConstraintsMap = new EMap<>();


    /** The triple constraints of the triple expression grouped by predicate. Memorized.
     * Uses the original expression if it is SORBE, or its {@link #sorbeForm} otherwise. */
    private Map<Node, List<TripleConstraint>> getTripleConstraintsGroupedByPredicate() {
        if (tripleConstraintsGroupedByPredicate == null) {
            tripleConstraintsGroupedByPredicate = getAllTripleConstraints().stream()
                    .collect(Collectors.groupingBy(TripleConstraint::getPredicate));
        }
        return tripleConstraintsGroupedByPredicate;
    }
    private Map<Node, List<TripleConstraint>> tripleConstraintsGroupedByPredicate;

    /** The triple constraints of a sub-expression of this triple expression. Memorized.
     * Uses the original expression if it is SORBE, or its {@link #sorbeForm} otherwise. */
    private Set<TripleConstraint> getTripleConstraintsOfSubExpr(TripleExpr subExpr) {
        return subExprToItsTripleConstraintsMap.computeIfAbsent(subExpr, e -> {
            Set<TripleConstraint> tripleConstraints = new ESet<>();
            AccumulationUtil.accumulateDirectTripleConstraints(subExpr, tripleConstraints);
            return tripleConstraints;
        });
    }
    private final EMap<TripleExpr, Set<TripleConstraint>> subExprToItsTripleConstraintsMap = new EMap<>();


    // --------------------------------------------------------------------------------------------------
    // Visitor-based traversals of the expression
    // --------------------------------------------------------------------------------------------------

    private static boolean computeIsSorbe(TripleExpr tripleExpr) {

        // List with at most one element, artefact for reusing accumulation code
        List<Object> acc = new ArrayList<>(1) {
            @Override
            public boolean add(Object o) {
                if (isEmpty())
                    super.add(o);
                return true;
            }
        };

        // Not the most natural or most efficient implementation, but reuses the recursive mechanism of expression walker
        TripleExprAccumulationVisitor<Object> step = new TripleExprAccumulationVisitor<>(acc) {
            @Override
            public void visit(TripleExprRef tripleExprRef) {
                accumulate(false);
            }

            @Override
            public void visit(TripleExprCardinality tripleExprCardinality) {
                Cardinality card = tripleExprCardinality.getCardinality();
                TripleExpr subExpr = tripleExprCardinality.getSubExpr();
                if (subExpr instanceof TripleConstraint)
                    return;
                if (card.equals(Cardinality.PLUS) && containsEmpty(subExpr, null))
                    accumulate(false);
                else if (!(card.equals(Cardinality.PLUS) || card.equals(Cardinality.STAR) ||
                        card.equals(Cardinality.OPT) || card.equals(IntervalComputation.ZERO_INTERVAL)))
                    accumulate(false);
            }
        };

        ExpressionWalker walker = ExpressionWalker.builder()
                .processTripleExprsWith(step)
                .build();
        tripleExpr.visit(walker);
        return acc.isEmpty();
    }

    private static boolean containsEmpty (TripleExpr tripleExpr, Function<Node, TripleExpr> tripleExprRefsDefs) {

        class CheckContainsEmpty implements TypedTripleExprVisitor<Boolean> {

            @Override
            public Boolean visit(TripleConstraint tripleConstraint) {
                return false;
            }

            @Override
            public Boolean visit(TripleExprEmpty tripleExprEmpty) {
                return true;
            }

            @Override
            public Boolean visit(EachOf eachOf) {
                return eachOf.getTripleExprs().stream()
                        .allMatch(subExpr -> subExpr.visit(this));
            }

            @Override
            public Boolean visit(OneOf oneOf) {
                return oneOf.getTripleExprs().stream()
                        .anyMatch(subExpr -> subExpr.visit(this));
            }

            @Override
            public Boolean visit(TripleExprCardinality tripleExprCardinality) {
                return (tripleExprCardinality.min() == 0) || tripleExprCardinality.getSubExpr().visit(this);
            }

            @Override
            public Boolean visit(TripleExprRef tripleExprRef) {
                return tripleExprRefsDefs.apply(tripleExprRef.getLabel()).visit(this);
            }
        }

        CheckContainsEmpty visitor = new CheckContainsEmpty();
        return tripleExpr.visit(visitor);
    }

    private static class CloneWithNullSemanticActionsAndEraseLabels implements TypedTripleExprVisitor<TripleExpr> {

        @Override
        public TripleExpr visit(EachOf eachOf) {
            List<TripleExpr> clonedSubExpressions = eachOf.getTripleExprs().stream()
                    .map(expr -> expr.visit(this))
                    .collect(Collectors.toList());
            return EachOf.create(clonedSubExpressions, null);
        }

        @Override
        public TripleExpr visit(OneOf oneOf) {
            List<TripleExpr> clonedSubExpressions = oneOf.getTripleExprs().stream()
                    .map(expr -> expr.visit(this))
                    .collect(Collectors.toList());
            return OneOf.create(clonedSubExpressions, null);
        }

        @Override
        public TripleExpr visit(TripleExprEmpty tripleExprEmpty) {
            return TripleExprEmpty.get();
        }

        @Override
        public TripleExpr visit(TripleExprRef tripleExprRef) {
            return TripleExprRef.create(tripleExprRef.getLabel());
        }

        @Override
        public TripleExpr visit(TripleConstraint tripleConstraint) {
            return TripleConstraint.create(null, tripleConstraint.getPredicate(),
                    tripleConstraint.isInverse(), tripleConstraint.getValueExpr(), null);
        }

        @Override
        public TripleExpr visit(TripleExprCardinality tripleExprCardinality) {
            TripleExpr clonedSubExpr = tripleExprCardinality.getSubExpr().visit(this);
            return TripleExprCardinality.create(clonedSubExpr, tripleExprCardinality.getCardinality(), null);
        }
    }

    private static class SorbeConstructor extends CloneWithNullSemanticActionsAndEraseLabels {

        private final EMap<TripleConstraint, List<TripleConstraint>> tripleConstraintCopiesMap;
        private final ShexSchema schema;

        SorbeConstructor(EMap<TripleConstraint, List<TripleConstraint>> tripleConstraintCopiesMap,
                         ShexSchema schema) {
            this.tripleConstraintCopiesMap = tripleConstraintCopiesMap;
            this.schema = schema;
        }

        @Override
        public TripleExpr visit(TripleConstraint tripleConstraint) {
            TripleConstraint copy = (TripleConstraint) super.visit(tripleConstraint);
            List<TripleConstraint> knownCopies
                    = tripleConstraintCopiesMap.computeIfAbsent(tripleConstraint, k -> new ArrayList<>());
            knownCopies.add(copy);
            return copy;
        }

        @Override
        public TripleExpr visit(TripleExprRef tripleExprRef) {
            return schema.getTripleExpr(tripleExprRef.getLabel()).visit(this);
        }

        @Override
        public TripleExpr visit(TripleExprCardinality tripleExprCardinality) {

            Cardinality card = tripleExprCardinality.getCardinality();

            Supplier<TripleExpr> clonedSubExpr = () ->
                    tripleExprCardinality.getSubExpr().visit(this);

            if (tripleExprCardinality.getSubExpr() instanceof TripleConstraint)
                // leave as is, just clone the subexpression
                return TripleExprCardinality.create(clonedSubExpr.get(), card, null);
            if (card.equals(Cardinality.PLUS) && containsEmpty(tripleExprCardinality, schema::getTripleExpr))
                // PLUS on an expression that contains the empty word becomes a star
                return TripleExprCardinality.create(clonedSubExpr.get(), Cardinality.STAR, null);
            else if (card.equals(Cardinality.OPT) || card.equals(Cardinality.STAR)
                    || card.equals(Cardinality.PLUS) || card.equals(IntervalComputation.ZERO_INTERVAL))
                // the standard intervals OPT STAR PLUS and ZERO are allowed
                return TripleExprCardinality.create(clonedSubExpr.get(), card, null);
            else {
                // non-standard cardinality on non-TripleConstraint -> create clones
                int nbClones;
                int nbOptClones;
                TripleExprCardinality remainingForUnbounded;

                if (card.max == Cardinality.UNBOUNDED) {
                    nbClones = card.min - 1;
                    nbOptClones = 0;
                    remainingForUnbounded = TripleExprCardinality.create(clonedSubExpr.get(),
                            Cardinality.PLUS, null);
                } else {
                    nbClones = card.min;
                    nbOptClones = card.max - card.min;
                    remainingForUnbounded = null;
                }

                List<TripleExpr> newSubExprs = new ArrayList<>(nbClones + nbOptClones + 1);
                for (int i = 0; i < nbClones; i++) {
                    newSubExprs.add(clonedSubExpr.get());
                }
                for (int i = 0; i < nbOptClones; i++) {
                    newSubExprs.add(TripleExprCardinality.create(clonedSubExpr.get(), Cardinality.OPT, null));
                }
                if (remainingForUnbounded != null)
                    newSubExprs.add(remainingForUnbounded);

                return EachOf.create(newSubExprs, null);
            }
        }
    }
}
