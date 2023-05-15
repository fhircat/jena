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

    public boolean containsSemActs () {
        return ! subexprsWithSemActs.isEmpty();
    }

    public List<TripleExpr> getSubExprsWithSemActs () {
        return subexprsWithSemActs;
    }

    /*package*/ List<TripleConstraint> sorbeTripleConstraints (TripleExpr sourceSubExpr, ValidationContext vCxt) {
        // TODO lazy computation here
        List<TripleConstraint> sourceTripleConstraints = ShapeEval.findTripleConstraints(vCxt, sourceSubExpr);
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

    public static SorbeTripleExpr create (TripleExpr tripleExpr, ShexSchema schema) {

        List<TripleExpr> subexprsWithSemActs = collectSubExprsWithSemActs(tripleExpr, schema);

        if (isSorbe(tripleExpr))
            return new SorbeTripleExpr(tripleExpr, tripleExpr, subexprsWithSemActs, null, null);

        Map<TripleConstraint, TripleConstraint> sorbeToSourceMap = new HashMap<>();
        Map<TripleConstraint, List<TripleConstraint>> sourceToSorbeMap = new HashMap<>();
        class SorbeConstructor extends CloneWithNullSemanticActionsAndEraseLabels {

            public TripleExpr getResult() {
                return result;
            }

            @Override
            public void visit(TripleConstraint tripleConstraint) {
                super.visit(tripleConstraint);
                TripleConstraint copy = (TripleConstraint) result;
                sorbeToSourceMap.put(copy, tripleConstraint);
                List<TripleConstraint> knownCopies = sourceToSorbeMap.computeIfAbsent(tripleConstraint, k -> new ArrayList<>());
                knownCopies.add(copy);
            }

            @Override
            public void visit(TripleExprRef tripleExprRef) {
                // will set result to a clone of the referenced triple expression
                schema.getTripleExpression(tripleExprRef.getLabel()).visit(this);
            }

            @Override
            public void visit(TripleExprCardinality tripleExprCardinality) {

                Cardinality card = tripleExprCardinality.getCardinality();

                Supplier<TripleExpr> clonedSubExpr = () -> {
                    tripleExprCardinality.getSubExpr().visit(this);
                    return result;
                };

                if (tripleExprCardinality.getSubExpr() instanceof TripleConstraint)
                    // leave as is, just clone the subexpression
                    this.result = TripleExprCardinality.create(clonedSubExpr.get(), card, null);
                if (card.equals(Cardinality.PLUS) && containsEmpty(tripleExprCardinality, schema))
                    // PLUS on an expression that contains the empty word becomes a star
                    this.result = TripleExprCardinality.create(clonedSubExpr.get(), Cardinality.STAR, null);
                else if (card.equals(Cardinality.OPT) || card.equals(Cardinality.STAR)
                        || card.equals(Cardinality.PLUS) || card.equals(IntervalComputation.ZERO_INTERVAL))
                    // the standard intervals OPT STAR PLUS and ZERO are allowed
                    this.result = TripleExprCardinality.create(clonedSubExpr.get(), card, null);
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

                    this.result = EachOf.create(newSubExprs, null);
                }
            }
        }

        SorbeConstructor constructor = new SorbeConstructor();
        tripleExpr.visit(constructor);
        TripleExpr sorbe = constructor.getResult();
        return new SorbeTripleExpr(tripleExpr, sorbe, subexprsWithSemActs, sorbeToSourceMap, sourceToSorbeMap);
    }

    public List<TripleExpr> collectSubExprsWithSemActs() {
        return subexprsWithSemActs;
    }

    private static boolean isSorbe (TripleExpr triplExpr) {

        class CheckIsSorbe implements TripleExprVisitor {

            private boolean result = true;
            boolean getResult () {
                return result;
            }

            @Override
            public void visit(TripleExprRef tripleExprRef) {
                result = false;
            }

            @Override
            public void visit(TripleExprCardinality tripleExprCardinality) {
                Cardinality card = tripleExprCardinality.getCardinality();
                TripleExpr subExpr = tripleExprCardinality.getSubExpr();
                if (subExpr instanceof TripleConstraint)
                    return;
                if (card.equals(Cardinality.PLUS) && containsEmpty(subExpr, null))
                    result = false;
                else if (! (card.equals(Cardinality.PLUS) || card.equals(Cardinality.STAR) ||
                        card.equals(Cardinality.OPT) || card.equals(IntervalComputation.ZERO_INTERVAL)))
                    result = false;
            }
        }
        CheckIsSorbe visitor = new CheckIsSorbe();
        TripleExprVisitor walker = new TripleExprWalker(visitor, null, null);
        triplExpr.visit(walker);
        return visitor.getResult();
    }

    private static List<TripleExpr> collectSubExprsWithSemActs(TripleExpr tripleExpr, ShexSchema schema) {

        class CollectSemActs implements TripleExprVisitor {
            List<TripleExpr> withSemAct = new ArrayList<>();

            List<TripleExpr> getResult() {
                return withSemAct;
            }
            @Override
            public void visit(TripleExprCardinality tripleExprCardinality) {
                if (tripleExprCardinality.getSemActs() != null)
                    withSemAct.add(tripleExprCardinality);
                tripleExprCardinality.getSubExpr().visit(this);
            }

            @Override
            public void visit(EachOf eachOf) {
                if (eachOf.getSemActs() != null)
                    withSemAct.add(eachOf);
                eachOf.getTripleExprs().forEach(te -> te.visit(this));
            }

            @Override
            public void visit(OneOf oneOf) {
                if (oneOf.getSemActs() != null)
                    withSemAct.add(oneOf);
                oneOf.getTripleExprs().forEach(te -> te.visit(this));
            }

            @Override
            public void visit(TripleExprEmpty tripleExprEmpty) {
                // empty
            }

            @Override
            public void visit(TripleExprRef tripleExprRef) {
                schema.getTripleExpression(tripleExprRef.getLabel()).visit(this);
            }

            @Override
            public void visit(TripleConstraint tripleConstraint) {
                if (tripleConstraint.getSemActs() != null)
                    withSemAct.add(tripleConstraint);
            }
        }

        CollectSemActs visitor = new CollectSemActs();
        tripleExpr.visit(visitor);
        return visitor.getResult();
    }

    private static boolean containsEmpty (TripleExpr tripleExpr, ShexSchema schema) {

        class CheckContainsEmpty implements TripleExprVisitor {

            private boolean result;

            boolean getResult() {
                return result;
            }

            @Override
            public void visit(TripleConstraint tripleConstraint) {
                result = false;
            }

            @Override
            public void visit(TripleExprEmpty tripleExprEmpty) {
                result = false;
            }

            @Override
            public void visit(EachOf eachOf) {
                result = eachOf.getTripleExprs().stream()
                        .allMatch(subExpr -> {subExpr.visit(this); return result;});
            }

            @Override
            public void visit(OneOf oneOf) {
                result = oneOf.getTripleExprs().stream()
                        .anyMatch(subExpr -> {subExpr.visit(this); return result;});
            }

            @Override
            public void visit(TripleExprCardinality tripleExprCardinality) {
                if (tripleExprCardinality.min() == 0) {
                    result = true;
                } else {
                    tripleExprCardinality.getSubExpr().visit(this);
                }
            }

            @Override
            public void visit(TripleExprRef tripleExprRef) {
                schema.getTripleExpression(tripleExprRef.getLabel()).visit(this);
            }
        }

        CheckContainsEmpty visitor = new CheckContainsEmpty();
        tripleExpr.visit(visitor);
        return visitor.getResult();
    }

    static class CloneWithNullSemanticActionsAndEraseLabels implements TripleExprVisitor {

        protected TripleExpr result;

        @Override
        public void visit(EachOf eachOf) {
            List<TripleExpr> clonedSubExpressions = new ArrayList<>(eachOf.getTripleExprs().size());
            for (TripleExpr te : eachOf.getTripleExprs()) {
                te.visit(this);
                clonedSubExpressions.add(result);
            }
            result = EachOf.create(clonedSubExpressions, null);
        }

        @Override
        public void visit(OneOf oneOf) {
            List<TripleExpr> clonedSubExpressions = new ArrayList<>(oneOf.getTripleExprs().size());
            for (TripleExpr te : oneOf.getTripleExprs()) {
                te.visit(this);
                clonedSubExpressions.add(result);
            }
            result = OneOf.create(clonedSubExpressions, null);
        }

        @Override
        public void visit(TripleExprEmpty tripleExprEmpty) {
            result = TripleExprEmpty.get();
        }

        @Override
        public void visit(TripleExprRef tripleExprRef) {
            result = TripleExprRef.create(tripleExprRef.getLabel());
        }

        @Override
        public void visit(TripleConstraint tripleConstraint) {
            result = TripleConstraint.create(null, tripleConstraint.getPredicate(),
                    tripleConstraint.isInverse(), tripleConstraint.getValueExpr(), null);
        }

        @Override
        public void visit(TripleExprCardinality tripleExprCardinality) {
            tripleExprCardinality.getSubExpr().visit(this);
            TripleExpr clonedSubExpr = result;
            result = TripleExprCardinality.create(clonedSubExpr, tripleExprCardinality.getCardinality(), null);
        }
    }
}
