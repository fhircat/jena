package org.apache.jena.shex.eval;

import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SorbeTripleExpr {

    private final TripleExpr original;
    /*package*/ final TripleExpr sorbe;

    private SorbeTripleExpr(TripleExpr original, TripleExpr sorbe) {
        this.original = original;
        this.sorbe = sorbe;
    }

    public void visit (TripleExprVisitor visitor) {
        sorbe.visit(visitor);
    }

    public static SorbeTripleExpr create (TripleExpr tripleExpr, ShexSchema schema) {

        class SorbeConstructor extends Clone_NullifySemanticActions_EraseLabels {

            public TripleExpr getResult() {
                return result;
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
                {
                    this.result = TripleExprCardinality.create(clonedSubExpr.get(), card, null);
                }
                if (card.equals(Cardinality.PLUS) && containsEmpty(tripleExprCardinality, schema))
                    // PLUS on an expression that contains the empty word becomes a star
                {
                    this.result = TripleExprCardinality.create(clonedSubExpr.get(), Cardinality.STAR, null);
                }
                else if (card.equals(Cardinality.OPT) || card.equals(Cardinality.STAR)
                        || card.equals(Cardinality.PLUS) || card.equals(IntervalComputation.ZERO_INTERVAL))
                    // the standard intervals OPT STAR PLUS and ZERO are allowed
                {
                    this.result = TripleExprCardinality.create(clonedSubExpr.get(), card, null);
                }
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
        return new SorbeTripleExpr(tripleExpr, constructor.getResult());
    }

    private static boolean containsEmpty (TripleExpr tripleExpr, ShexSchema schema) {

        class CheckContainsEmpty implements TripleExprVisitor {

            private boolean result;

            public Boolean getResult() {
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
                for (TripleExpr subExpr : eachOf.getTripleExprs()) {
                    subExpr.visit(this);
                    if (!result)
                        return;
                }
            }

            @Override
            public void visit(OneOf oneOf) {
                for (TripleExpr subExpr : oneOf.getTripleExprs()) {
                    subExpr.visit(this);
                    if (result)
                        return;
                }
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

    static class Clone_NullifySemanticActions_EraseLabels implements TripleExprVisitor {

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
