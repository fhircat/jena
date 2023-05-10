package org.apache.jena.shex.eval;

import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.*;

import java.util.ArrayList;
import java.util.List;

public class SORBETripleExpr {

    private final TripleExpression original;
    private TripleExpression sorbe;

    private SORBETripleExpr(TripleExpression original, TripleExpression sorbe) {
        this.original = original;
        this.sorbe = sorbe;
    }

    public void visit (TripleExprVisitor visitor) {
        sorbe.visit(visitor);
    }

    public static SORBETripleExpr create (TripleExpression tripleExpr, ShexSchema schema) {

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

                expr.target().visit(this);
                if (/* expr.cardinality.equals(Intreval.PLUS)*/expr.min() == 1 && expr.max() == Cardinality.UNBOUNDED
                        && containsEmpty(expr, schema)) {
                    setResult(new TripleExprCardinality(expr.target(), Cardinality.STAR, null));
                } else if /*cardinality is one of +, *, ? or zero, TODO zero needed ?*/
                        (expr.max() == Cardinality.UNBOUNDED && (expr.min() == 0 || expr.min() == 1)
                                || expr.min() == 0 && expr.max() == 1) {
                            //setResult(new TripleExprCardinality(expr.target(), ))
                }

                /*
                CheckIfContainsEmpty visitor = new CheckIfContainsEmpty();
                expr.accept(visitor);
                expr.getSubExpression().accept(this);
                if (expr.getCardinality().equals(Interval.PLUS) & visitor.result) {
                    result = new RepeatedTripleExpression(result,Interval.STAR);
                    setTripleLabel(result,expr);
                } else if(expr.getCardinality().equals(Interval.PLUS)
                        || expr.getCardinality().equals(Interval.STAR)
                        || expr.getCardinality().equals(Interval.OPT)
                        || expr.getCardinality().equals(Interval.ZERO)){
                    result = new RepeatedTripleExpression(result,expr.getCardinality());
                    setTripleLabel(result,expr);
                } else {
                    Interval card = expr.getCardinality();
                    int nbClones = 0;
                    int	nbOptClones = 0;
                    List<TripleExpr> clones = new ArrayList<>();

                    if (card.max == Interval.UNBOUND) {
                        nbClones = card.min -1;
                        TripleExpr tmp = new RepeatedTripleExpression(result, Interval.PLUS);
                        setTripleLabel(tmp,expr);
                        clones.add(tmp);
                    }else {
                        nbClones = card.min;
                        nbOptClones = card.max - card.min;
                    }

                    for (int i=0; i<nbClones;i++) {
                        expr.getSubExpression().accept(this);
                        clones.add(result);
                    }
                    for (int i=0; i<nbOptClones;i++) {
                        expr.getSubExpression().accept(this);
                        TripleExpr tmp = new RepeatedTripleExpression(result, Interval.OPT);
                        setTripleLabel(tmp,expr);
                        clones.add(tmp);
                    }
                    if (clones.size()==1)
                        result = clones.get(0);
                    else {
                        result = new EachOf(clones);
                        setTripleLabel(result,expr);
                    }
                }*/

            }
        }

        SorbeConstructor constructor = new SorbeConstructor();
        tripleExpr.visit(constructor);
        return new SORBETripleExpr(tripleExpr, constructor.getResult());
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
                    expr.target().visit(this);
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
