package org.apache.jena.shex.eval;

import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.*;

import java.util.ArrayList;
import java.util.List;

public class SorbeTripleExpr {

    private final TripleExpression original;
    /*package*/ final TripleExpression sorbe;

    private SorbeTripleExpr(TripleExpression original, TripleExpression sorbe) {
        this.original = original;
        this.sorbe = sorbe;
    }

    public void visit (TripleExprVisitor visitor) {
        sorbe.visit(visitor);
    }

    public static SorbeTripleExpr create (TripleExpression tripleExpr, ShexSchema schema) {

        class SorbeConstructor implements TripleExprVisitor {

            TripleExpression result;

            public TripleExpression getResult() {
                return result;
            }
            public void setResult(TripleExpression result) {
                this.result = result;
            }

            @Override
            public void visit(EachOf tripleExpr) {
                List<TripleExpression> subExpressions = new ArrayList<>(tripleExpr.expressions().size());
                for (TripleExpression te : tripleExpr.expressions()) {
                    te.visit(this);
                    subExpressions.add(getResult());
                }
                setResult(EachOf.create(subExpressions, null));
            }

            @Override
            public void visit(OneOf tripleExpr) {
                List<TripleExpression> subExpressions = new ArrayList<>(tripleExpr.expressions().size());
                for (TripleExpression te : tripleExpr.expressions()) {
                    te.visit(this);
                    subExpressions.add(getResult());
                }
                setResult(OneOf.create(subExpressions, null));
            }

            @Override
            public void visit(TripleExprEmpty tripleExpr) {
                setResult(TripleExprEmpty.get());
            }

            @Override
            public void visit(TripleExprRef tripleExpr) {
                schema.getTripleExpression(tripleExpr.getRef()).visit(this);
            }

            @Override
            public void visit(TripleConstraint tripleExpr) {
                setResult(new TripleConstraint(tripleExpr.label(), tripleExpr.getPredicate(), tripleExpr.reverse(),
                        tripleExpr.getShapeExpression(), null));
            }

            @Override
            public void visit(TripleExprCardinality expr) {
                TripleExprVisitor.super.visit(expr);

                TripleExpression subExpr = expr.getSubExpr();
                subExpr.visit(this);
                Cardinality card = expr.getCardinality();
                if (card.equals(Cardinality.PLUS) && containsEmpty(expr, schema))
                    setResult(new TripleExprCardinality(subExpr, Cardinality.STAR, null));
                else if (card.equals(Cardinality.OPT) || card.equals(Cardinality.STAR)
                        || card.equals(Cardinality.PLUS) || card.equals(IntervalComputation.ZERO_INTERVAL))
                    setResult(new TripleExprCardinality(subExpr, card, null));
                else {
                    int nbClones;
                    int nbOptClones;
                    TripleExprCardinality unboundedQuotient;

                    if (card.max == Cardinality.UNBOUNDED) {
                        nbClones = card.min - 1;
                        nbOptClones = 0;
                        unboundedQuotient = new TripleExprCardinality(getResult(), Cardinality.PLUS, null);
                    } else {
                        nbClones = card.min;
                        nbOptClones = card.max - card.min;
                        unboundedQuotient = null;
                    }

                    List<TripleExpression> newSubExprs = new ArrayList<>(nbClones + nbOptClones + 1);
                    for (int i = 0; i < nbClones; i++) {
                        subExpr.visit(this);
                        newSubExprs.add(getResult());
                    }
                    for (int i = 0; i < nbOptClones; i++) {
                        subExpr.visit(this);
                        newSubExprs.add(new TripleExprCardinality(getResult(), Cardinality.OPT, null));
                    }
                    if (unboundedQuotient != null)
                        newSubExprs.add(unboundedQuotient);

                    setResult(EachOf.create(newSubExprs, null));
                }
            }
        }

        SorbeConstructor constructor = new SorbeConstructor();
        tripleExpr.visit(constructor);
        return new SorbeTripleExpr(tripleExpr, constructor.getResult());
    }

    private static boolean containsEmpty (TripleExpression tripleExpr, ShexSchema schema) {

        class CheckContainsEmpty implements TripleExprVisitor {

            private boolean result;

            public Boolean getResult() {
                return result;
            }

            @Override
            public void visit(TripleConstraint tc) {
                result = false;
            }

            @Override
            public void visit(TripleExprEmpty expr) {
                result = false;
            }

            @Override
            public void visit(EachOf expr) {
                for (TripleExpression subExpr : expr.expressions()) {
                    subExpr.visit(this);
                    if (!result)
                        return;
                }
            }

            @Override
            public void visit(OneOf expr) {
                for (TripleExpression subExpr : expr.expressions()) {
                    subExpr.visit(this);
                    if (result)
                        return;
                }
            }

            @Override
            public void visit(TripleExprCardinality expr) {
                if (expr.min() == 0) {
                    result = true;
                } else {
                    expr.getSubExpr().visit(this);
                }
            }

            @Override
            public void visit(TripleExprRef expr) {
                schema.getTripleExpression(expr.getRef()).visit(this);
            }
        }

        CheckContainsEmpty visitor = new CheckContainsEmpty();
        tripleExpr.visit(visitor);
        return visitor.getResult();
    }

}
