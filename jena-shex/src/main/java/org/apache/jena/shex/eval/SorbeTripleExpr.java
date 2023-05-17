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

package org.apache.jena.shex.eval;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.sys.ValidationContext;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SorbeTripleExpr {

    private final TripleExpr sourceTripleExpr;
    /*package*/ final TripleExpr sorbe;
    private List<TripleConstraint> allSorbeTripleConstraints;
    private final List<TripleExpr> srcSubExprsWithSemActs;
    // With every original triple subExpr having semantic actions, maps its triple constraints. Set is necessary here
    private final Map<TripleExpr, Set<TripleConstraint>> srcSubExprToItsSorbeTripleConstraintsMap = new HashMap<>();
    private final Map<TripleExpr, List<TripleConstraint>> sorbeSubExprToItsSorbeTripleConstraintsMap = new HashMap<>();
    // With every triple constraint associates its copies
    private final Map<TripleConstraint, List<TripleConstraint>> tripleConstraintsCopiesMap;


    public List<TripleConstraint> getAllSorbeTripleConstraints() {
        if (allSorbeTripleConstraints == null) {
            allSorbeTripleConstraints = new ArrayList<>();
            collectTripleConstraints(sorbe, false, null, allSorbeTripleConstraints);
        }
        return allSorbeTripleConstraints;
    }


    private SorbeTripleExpr(TripleExpr sourceTripleExpr, TripleExpr sorbe,
                            List<TripleExpr> srcSubExprsWithSemActs,
                            Map<TripleConstraint, TripleConstraint> sorbeToSourceMap,
                            Map<TripleConstraint, List<TripleConstraint>> sourceToSorbeMap) {
        this.sourceTripleExpr = sourceTripleExpr;
        this.sorbe = sorbe;
        this.srcSubExprsWithSemActs = srcSubExprsWithSemActs;
        this.tripleConstraintsCopiesMap = sourceToSorbeMap;
    }

    /** Computes the entries for the #srcSubExprToSorbeTripleConstraintsMap */
    private Set<TripleConstraint> getSorbeTripleConstraintsOfSourceSubExpr(TripleExpr srcSubExpr, ValidationContext vCxt) {
        return srcSubExprToItsSorbeTripleConstraintsMap.computeIfAbsent(srcSubExpr, e -> {
            Set<TripleConstraint> sourceTripleConstraintsSet = new HashSet<>();
            collectTripleConstraints(srcSubExpr, true, vCxt.getShapes(), sourceTripleConstraintsSet);
            if (sourceTripleExpr == sorbe)
                return sourceTripleConstraintsSet;
            else
                return sourceTripleConstraintsSet.stream()
                        .flatMap(tc -> tripleConstraintsCopiesMap.get(tc).stream())
                        .collect(Collectors.toSet());
        });
    }

    public static SorbeTripleExpr create(TripleExpr tripleExpr, ShexSchema schema) {

        List<TripleExpr> subExprsWithSemActs = collectSubExprsWithSemActs(tripleExpr, schema);

        if (isSorbe(tripleExpr))
            return new SorbeTripleExpr(tripleExpr, tripleExpr, subExprsWithSemActs, null, null);

        Map<TripleConstraint, TripleConstraint> sorbeToSourceMap = new HashMap<>();
        Map<TripleConstraint, List<TripleConstraint>> sourceToSorbeMap = new HashMap<>();

        class SorbeConstructor extends CloneWithNullSemanticActionsAndEraseLabels {

            @Override
            public TripleExpr visit(TripleConstraint tripleConstraint) {
                TripleConstraint copy = (TripleConstraint) super.visit(tripleConstraint);
                sorbeToSourceMap.put(copy, tripleConstraint);
                List<TripleConstraint> knownCopies
                        = sourceToSorbeMap.computeIfAbsent(tripleConstraint, k -> new ArrayList<>());
                knownCopies.add(copy);
                return copy;
            }

            @Override
            public TripleExpr visit(TripleExprRef tripleExprRef) {
                // will set result to a clone of the referenced triple expression
                return schema.getTripleExpression(tripleExprRef.getLabel()).visit(this);
            }

            @Override
            public TripleExpr visit(TripleExprCardinality tripleExprCardinality) {

                Cardinality card = tripleExprCardinality.getCardinality();

                Supplier<TripleExpr> clonedSubExpr = () ->
                    tripleExprCardinality.getSubExpr().visit(this);

                if (tripleExprCardinality.getSubExpr() instanceof TripleConstraint)
                    // leave as is, just clone the subexpression
                    return TripleExprCardinality.create(clonedSubExpr.get(), card, null);
                if (card.equals(Cardinality.PLUS) && containsEmpty(tripleExprCardinality, schema))
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

        SorbeConstructor constructor = new SorbeConstructor();
        TripleExpr sorbe = tripleExpr.visit(constructor);
        return new SorbeTripleExpr(tripleExpr, sorbe, subExprsWithSemActs, sorbeToSourceMap, sourceToSorbeMap);
    }


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

        // Not the most natural or most efficient implementation, but reuses recursive mechanism of accumulation walker
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

        VoidWalker walker = new VoidWalker.Builder()
                .processTripleExprsWith(step)
                .build();
        tripleExpr.visit(walker);
        return acc.isEmpty();
    }


    // Returns true if the bag has value 0 for all every triple constraint originating in the subexpression (which must be a sub expression of this sorbe expression)
    /*package*/ boolean isEmptySubbag(Map<TripleConstraint, Integer> bag, TripleExpr subExpr) {
        return getSorbeTripleConstraintsOfSorbeSubExpr(subExpr).stream()
                .allMatch(tc -> bag.get(tc) == 0);
    }

    private List<TripleConstraint> getSorbeTripleConstraintsOfSorbeSubExpr (TripleExpr sorbeSubExpr) {
        return sorbeSubExprToItsSorbeTripleConstraintsMap.computeIfAbsent(sorbeSubExpr, e -> {
            List<TripleConstraint> tripleConstraints = new ArrayList<>();
            collectTripleConstraints(sorbeSubExpr, false, null, tripleConstraints);
            return tripleConstraints;
        });
    }


    private static boolean containsEmpty (TripleExpr tripleExpr, ShexSchema schema) {

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
                return schema.getTripleExpression(tripleExprRef.getLabel()).visit(this);
            }
        }

        CheckContainsEmpty visitor = new CheckContainsEmpty();
        return tripleExpr.visit(visitor);
    }

    public List<Pair<TripleExpr, Set<Triple>>> getSemActsSubExprsAndTheirMatchedTriples(Map<Triple, TripleConstraint> matching,
                                                                                        ValidationContext vCxt) {
        List<Pair<TripleExpr, Set<Triple>>> result = new ArrayList<>();

        for (TripleExpr srcSubExpr : srcSubExprsWithSemActs) {
            Set<TripleConstraint> sorbeTripleConstraints = getSorbeTripleConstraintsOfSourceSubExpr(srcSubExpr, vCxt);
            Set<Triple> matchedTriples = matching.entrySet().stream()
                    .filter(e -> sorbeTripleConstraints.contains(e.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            result.add(new ImmutablePair<>(srcSubExpr, matchedTriples));
        }
        return result;
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

    private static List<TripleExpr> collectSubExprsWithSemActs(TripleExpr tripleExpr, ShexSchema schema) {

        List<TripleExpr> result = new ArrayList<>();

        TripleExprAccumulationVisitor<TripleExpr> accumulator = new TripleExprAccumulationVisitor<>(result) {

            private void _visit(TripleExpr tripleExpr) {
                if (tripleExpr.getSemActs() != null)
                    accumulate(tripleExpr);
            }

            @Override
            public void visit(TripleExprCardinality tripleExprCardinality) {
                _visit(tripleExprCardinality);
            }

            @Override
            public void visit(EachOf eachOf) {
                _visit(eachOf);
            }

            @Override
            public void visit(OneOf oneOf) {
                _visit(oneOf);
            }

            @Override
            public void visit(TripleExprEmpty tripleExprEmpty) {
                _visit(tripleExpr);
            }

            @Override
            public void visit(TripleExprRef tripleExprRef) {
                _visit(tripleExprRef);
            }

            @Override
            public void visit(TripleConstraint tripleConstraint) {
                _visit(tripleConstraint);
            }
        };

        VoidWalker walker = new VoidWalker.Builder()
                .processTripleExprsWith(accumulator)
                .followTripleExprRefs(schema)
                .build();
        tripleExpr.visit(walker);
        return result;
    }

    private static void collectTripleConstraints (TripleExpr tripleExpr,
                                                 boolean followTripleExprRefs, ShexSchema schema,
                                                 Collection<TripleConstraint> acc) {

        TripleExprAccumulationVisitor<TripleConstraint> step = new TripleExprAccumulationVisitor<>(acc) {
            @Override
            public void visit(TripleConstraint tripleConstraint) {
                accumulate(tripleConstraint);
            }
        };


        VoidWalker.Builder builder = new VoidWalker.Builder();
        builder.processTripleExprsWith(step);
        if (followTripleExprRefs)
            builder.followTripleExprRefs(schema);
        tripleExpr.visit(builder.build());
    }

}
