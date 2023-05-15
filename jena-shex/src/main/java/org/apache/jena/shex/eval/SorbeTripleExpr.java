package org.apache.jena.shex.eval;

import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.sys.ValidationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SorbeTripleExpr {

    private final TripleExpr sourceTripleExpr;
    /*package*/ final TripleExpr sorbe;
    private final List<TripleExpr> subexprsWithSemActs;
    private final Map<TripleConstraint, TripleConstraint> sorbeToSourceMap;
    private final Map<TripleConstraint, List<TripleConstraint>> sourceToSorbeMap;

    public boolean containsSemActs() {
        return !subexprsWithSemActs.isEmpty();
    }

    public List<TripleExpr> getSubExprsWithSemActs() {
        return subexprsWithSemActs;
    }

    /*package*/ List<TripleConstraint> sorbeTripleConstraints(TripleExpr sourceSubExpr, ValidationContext vCxt) {
        // TODO lazy computation here
        List<TripleConstraint> sourceTripleConstraints = Util.collectTripleConstraints(sourceSubExpr,
                true, vCxt.getShapes());
        if (sourceTripleExpr == sorbe)
            return sourceTripleConstraints;
        else
            return sourceToSorbeMap.entrySet().stream()
                    .filter(e -> sourceTripleConstraints.contains(e.getKey()))
                    .flatMap(e -> e.getValue().stream())
                    .collect(Collectors.toList());

    }


    private SorbeTripleExpr(TripleExpr sourceTripleExpr, TripleExpr sorbe, List<TripleExpr> subExprsWithSemActs,
                            Map<TripleConstraint, TripleConstraint> sorbeToSourceMap,
                            Map<TripleConstraint, List<TripleConstraint>> sourceToSorbeMap) {
        this.sourceTripleExpr = sourceTripleExpr;
        this.sorbe = sorbe;
        this.subexprsWithSemActs = subExprsWithSemActs;
        this.sorbeToSourceMap = sorbeToSourceMap;
        this.sourceToSorbeMap = sourceToSorbeMap;
    }

    public static SorbeTripleExpr create(TripleExpr tripleExpr, ShexSchema schema) {

        List<TripleExpr> subexprsWithSemActs = Util.collectSubExprWithSemActs(tripleExpr, true, schema);

        if (isSorbe(tripleExpr))
            return new SorbeTripleExpr(tripleExpr, tripleExpr, subexprsWithSemActs, null, null);

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
        return new SorbeTripleExpr(tripleExpr, sorbe, subexprsWithSemActs, sorbeToSourceMap, sourceToSorbeMap);
    }


    private static boolean isSorbe(TripleExpr tripleExpr) {

        // List with most one element
        List<Object> acc = new ArrayList<>(1) {
            @Override
            public boolean add(Object o) {
                if (isEmpty())
                    super.add(o);
                return true;
            }
        };

        // Not the most natural or most efficient implementation, but reuses recursive mechanism of accumulation walker
        TripleExprAccumulationVisitor2<Object> step = new TripleExprAccumulationVisitor2<>(acc) {
            @Override
            public Void visit(TripleExprRef tripleExprRef) {
                acc.add(false);
                return null;
            }

            @Override
            public Void visit(TripleExprCardinality tripleExprCardinality) {
                Cardinality card = tripleExprCardinality.getCardinality();
                TripleExpr subExpr = tripleExprCardinality.getSubExpr();
                if (subExpr instanceof TripleConstraint)
                    return null;
                if (card.equals(Cardinality.PLUS) && containsEmpty(subExpr, null))
                    acc.add(false);
                else if (!(card.equals(Cardinality.PLUS) || card.equals(Cardinality.STAR) ||
                        card.equals(Cardinality.OPT) || card.equals(IntervalComputation.ZERO_INTERVAL)))
                    acc.add(false);
                return null;
            }
        };

        TripleExprWalker2 walker = new TripleExprWalker2(step, null, false, null);
        tripleExpr.visit(walker);
        return acc.isEmpty();
    }

    private static boolean containsEmpty (TripleExpr tripleExpr, ShexSchema schema) {

        class CheckContainsEmpty implements TripleExprVisitor2<Boolean> {

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

    static class CloneWithNullSemanticActionsAndEraseLabels implements TripleExprVisitor2<TripleExpr> {

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
}
