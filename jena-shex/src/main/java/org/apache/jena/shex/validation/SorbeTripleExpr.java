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
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.calc.TripleExprAccumulationVisitor;
import org.apache.jena.shex.calc.TypedTripleExprVisitor;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** A SORBE triple expression is a triple expression that satisfies:
 * <ul>
 *     <li>it does not contain triple expression references</li>
 *     <li>cardinalities other than ?, *, + are appear only on triple constraints</li>
 *     <li>cardinality + is appears only on sub-expressions that cannot be satisfied by an empty neighbourhood</li>
 * </ul>
 *
 * <p>
 * Given a triple expression, called <em>origin</em>, one can construct an equivalent SORBE triple expression.
 * This involves replacing triple expression references by their definition, and copying some of the subexpressions.
 * For instance, if <pre>tc1, tc2</pre> are triple constraints, then the triple expression <pre>(tc1 | tc2) {2;4}</pre>
 * is not SORBE, but it is equivalent to the SORBE triple expression <pre>(tc1 | tc2) ; (tc1 | tc2) ; (tc1 | tc2)? ; (tc1 | tc2)?</pre>
 * Such equivalent SORBE triple expression is constructed using {@link #create(TripleExpr, ShexSchema)}.
 * </p><p>
 * When copying occurs, all occurrences of the triple constraint <pre>tc1</pre> in the SORBE triple expression <em>originate</em> from the triple constraint <pre>tc1</pre> from the origin expression.
 * Given a sub-expression of the <em>origin</em> triple expression, we can retrieve all triple constraints
 * This origin information is used to determine which triples matched the
 * </p>
 */
/*package*/ class SorbeTripleExpr {

    private final TripleExpr originTripleExpr;
    private final TripleExpr sorbe;
    // With every triple constraint's id associates its copies in the sorbe expression. Null if the source triple expr is sorbe
    private final Map<Integer, List<TripleConstraint>> tripleConstraintCopiesMap;
    private final List<TripleExpr> originSubExprsWithSemActs;

    private SorbeTripleExpr(TripleExpr originTripleExpr, TripleExpr sorbe,
                            List<TripleExpr> originSubExprsWithSemActs,
                            Map<Integer, List<TripleConstraint>> tripleConstraintCopiesMap) {
        this.originTripleExpr = originTripleExpr;
        this.sorbe = sorbe;
        this.originSubExprsWithSemActs = originSubExprsWithSemActs;
        this.tripleConstraintCopiesMap = tripleConstraintCopiesMap;
    }

    /*package*/ static SorbeTripleExpr create(TripleExpr tripleExpr, ShexSchema schema) {

        List<TripleExpr> subExprsWithSemActs
                = AccumulationUtil.collectSubExprsWithSemActs(tripleExpr, schema::getTripleExpr);

        if (isSorbe(tripleExpr))
            return new SorbeTripleExpr(tripleExpr, tripleExpr, subExprsWithSemActs, null);

        Map<Integer, List<TripleConstraint>> tripleConstraintCopiesMap = new HashMap<>();
        SorbeConstructor constructor = new SorbeConstructor(tripleConstraintCopiesMap, schema);
        TripleExpr sorbe = tripleExpr.visit(constructor);
        return new SorbeTripleExpr(tripleExpr, sorbe, subExprsWithSemActs, tripleConstraintCopiesMap);
    }

    /** With every triple in the input, associates the triple constraints of this SORBE triple expression that have the same predicate as the triple. */
    /*package*/ Map<Triple, List<TripleConstraint>> getPredicateBasedPreMatching(Collection<Triple> triples) {

        Map<Node, List<TripleConstraint>> tcsByPredicate = getSorbeTripleConstraintsGroupedByPredicate();
        return triples.stream().collect(Collectors.toMap(Function.identity(),
                t -> new ArrayList<>(tcsByPredicate.get(t.getPredicate()))));
    }

    /*package*/ Cardinality computeInterval (Map<Triple, TripleConstraint> matching) {
        Bag bag = Bag.fromMatching(matching, getAllSorbeTripleConstraints());
        return sorbe.visit(new IntervalComputation(this, bag));
    }

    /** Checks whether the bag has value 0 for every triple constraint originating in the subexpression.
     * @param bag The bag to be tested
     * @param sorbeSubExpr A sub-expression of this SORBE triple expression
     */
    /*package*/ boolean isEmptySubbag(Bag bag, TripleExpr sorbeSubExpr) {
        return getSorbeTripleConstraintsOfSorbeSubExpr(sorbeSubExpr).stream()
                .allMatch(tc -> bag.getCard(tc) == 0);
    }

    /** With every origin sub-expression having semantic actions, associates the triples that the given matching matches to this sub-expression.
     *
     * @param matching Matching to SORBE triple constraints
     * @param vCxt
     * @return
     */
    // Cannot return a map here because two triple expressions can be equal (wrt Object#equals) but distinct in the AST
    /*package*/ List<Pair<TripleExpr, Set<Triple>>> getSemActsSubExprsAndTheirMatchedTriples(
            Map<Triple, TripleConstraint> matching, ValidationContext vCxt) {

        return originSubExprsWithSemActs.stream()
                .map(originSubExpr -> new ImmutablePair<>(originSubExpr,
                        triplesMatchedInOriginSubExpr(matching, originSubExpr, vCxt)))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Accessors and memoized informations
    // ---------------------------------------------------------------------------------------------------------

    /** The triple constraints of this SORBE triple expression. Memorized. */
    private List<TripleConstraint> getAllSorbeTripleConstraints() {
        if (allSorbeTripleConstraints == null) {
            allSorbeTripleConstraints = new ArrayList<>();
            AccumulationUtil.accumulateDirectTripleConstraints(sorbe, allSorbeTripleConstraints);
        }
        return allSorbeTripleConstraints;
    }
    private List<TripleConstraint> allSorbeTripleConstraints;

    /** The triple constraints of this SORBE triple expression which origins are in the given origin sub-expression.
     * Memorized
     * @param originSubExpr Must be a sub-expression of the origin triple expression that was used to create this SORBE triple expression.
     * @param vCxt
     * @return
     */
    private Set<Integer> getSorbeTripleConstraintsOfOriginSubExpr(TripleExpr originSubExpr, ValidationContext vCxt) {
        return srcSubExprToItsSorbeTripleConstraintsMap.computeIfAbsent(originSubExpr.id, e -> {
            List<TripleConstraint> sourceTripleConstraints = new ArrayList<>();
            AccumulationUtil.accumulateTripleConstraintsFollowTripleExprReferences(originSubExpr,
                    vCxt::getTripleExpr, sourceTripleConstraints);
            if (originTripleExpr == sorbe)
                return sourceTripleConstraints.stream()
                        .map(tc -> tc.id)
                        .collect(Collectors.toSet());
            else
                return sourceTripleConstraints.stream()
                        .flatMap(tc -> tripleConstraintCopiesMap.get(tc.id).stream())
                        .map(tc -> tc.id)
                        .collect(Collectors.toSet());
        });
    }
    private final Map<Integer, Set<Integer>> srcSubExprToItsSorbeTripleConstraintsMap = new HashMap<>();


    /** The triple constraints of the SORBE expression grouped by predicate. Memorized. */
    private Map<Node, List<TripleConstraint>> getSorbeTripleConstraintsGroupedByPredicate() {
        if (tripleConstraintsGroupedByPredicate == null) {
            tripleConstraintsGroupedByPredicate = getAllSorbeTripleConstraints().stream()
                    .collect(Collectors.groupingBy(TripleConstraint::getPredicate));
        }
        return tripleConstraintsGroupedByPredicate;
    }
    private Map<Node, List<TripleConstraint>> tripleConstraintsGroupedByPredicate;

    /** The triple constraints of a sub-expression of this SORBE triple expression. Memorized. */
    private List<TripleConstraint> getSorbeTripleConstraintsOfSorbeSubExpr (TripleExpr sorbeSubExpr) {
        return sorbeSubExprToItsSorbeTripleConstraintsMap.computeIfAbsent(sorbeSubExpr.id, e -> {
            List<TripleConstraint> tripleConstraints = new ArrayList<>();
            AccumulationUtil.accumulateDirectTripleConstraints(sorbeSubExpr, tripleConstraints);
            return tripleConstraints;
        });
    }
    private final Map<Integer, List<TripleConstraint>> sorbeSubExprToItsSorbeTripleConstraintsMap = new HashMap<>();

    /** The triples that are matched with an origin sub-expression.
     *
     * @param sorbeMatching Matching to sorbe triple constraints
     * @param originSubExpr Origin sub-expression
     * @param vCxt
     * @return The set of triples that {@code sorbeMatching} matches to some sorbe triple constraint which origin is in {@code originSubExpr}
     */
    private Set<Triple> triplesMatchedInOriginSubExpr(Map<Triple, TripleConstraint> sorbeMatching,
                                                      TripleExpr originSubExpr,
                                                      ValidationContext vCxt) {

        Set<Integer> sorbeTripleConstraints = getSorbeTripleConstraintsOfOriginSubExpr(originSubExpr, vCxt);
        return sorbeMatching.entrySet().stream()
                .filter(e -> sorbeTripleConstraints.contains(e.getValue().id))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    // --------------------------------------------------------------------------------------------------
    // Visitor-based traversals of the expression
    // --------------------------------------------------------------------------------------------------


    private static boolean isSorbe(TripleExpr tripleExpr) {

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

        private final Map<Integer, List<TripleConstraint>> tripleConstraintCopiesMap;
        private final ShexSchema schema;

        SorbeConstructor(Map<Integer, List<TripleConstraint>> tripleConstraintCopiesMap,
                         ShexSchema schema) {
            this.tripleConstraintCopiesMap = tripleConstraintCopiesMap;
            this.schema = schema;
        }

        @Override
        public TripleExpr visit(TripleConstraint tripleConstraint) {
            TripleConstraint copy = (TripleConstraint) super.visit(tripleConstraint);
            List<TripleConstraint> knownCopies
                    = tripleConstraintCopiesMap.computeIfAbsent(tripleConstraint.id, k -> new ArrayList<>());
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
