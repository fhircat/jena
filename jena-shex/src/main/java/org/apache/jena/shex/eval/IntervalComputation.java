package org.apache.jena.shex.eval;

import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.sys.ValidationContext;

import java.util.List;
import java.util.Map;

class IntervalComputation implements TripleExprVisitor {

    private static Cardinality ZERO = new Cardinality(0, 0);
    private static Cardinality EMPTY = new Cardinality(2, 1);

    private final Map<TripleConstraint, Integer> bag;
    private final ValidationContext vCxt;
    Cardinality result;

    public IntervalComputation(Map<TripleConstraint, Integer> bag, ValidationContext vCxt) {
        this.bag = bag;
        this.vCxt = vCxt;
        result = null;
    }

    private void setResult(Cardinality result) {
        this.result = result;
    }

    public Cardinality getResult() {
        return this.result;
    }

    @Override
    public void visit(TripleConstraint tc) {

        int nbOcc = bag.get(tc);
        setResult(new Cardinality(nbOcc, nbOcc));
    }

    @Override
    public void visit(TripleExprEmpty emptyTripleExpression) {
        setResult(Cardinality.STAR);
    }

    @Override
    public void visit(OneOf expr) {
        Cardinality res = ZERO; // the neutral element for addition

        for (TripleExpression subExpr : expr.expressions()) {
            subExpr.visit(this);
            res = add(res, getResult());
        }
        setResult(res);
    }

    @Override
    public void visit(EachOf expr) {
        Cardinality res = Cardinality.STAR; // the neutral element for intersection

        for (TripleExpression subExpr : expr.expressions()) {
            subExpr.visit(this);
            res = inter(res, getResult());
        }
        setResult(res);
    }

    @Override
    public void visit(TripleExprCardinality expression) {

        Cardinality card = new Cardinality(expression.min(), expression.max());
        TripleExpression subExpr = expression.target();
        boolean isEmptySubbag = isEmptySubbag(bag, expression, vCxt);

        if (card.equals(Cardinality.STAR)) {
            if (isEmptySubbag) {
                setResult(Cardinality.STAR);
            } else {
                subExpr.visit(this);
                if (!this.getResult().equals(EMPTY)) {
                    setResult(Cardinality.PLUS);
                }
            }
        } else if (card.equals(Cardinality.PLUS)) {
            if (isEmptySubbag) {
                setResult(ZERO);
            } else {
                subExpr.visit(this);
                if (!this.getResult().equals(EMPTY)) {
                    setResult(new Cardinality(1, getResult().max));
                } else {
                    setResult(EMPTY);
                }
            }
        } else if (card.equals(Cardinality.OPT)) {
            subExpr.visit(this);
            setResult(add(getResult(), Cardinality.STAR));
        } else if (subExpr instanceof TripleConstraint) {
            int nbOcc = bag.get((TripleConstraint) subExpr);
            setResult(div(nbOcc, card));
        } else if (card.equals(ZERO)) {
            if (isEmptySubbag) {
                setResult(Cardinality.STAR);
            } else {
                setResult(EMPTY);
            }
        } else {
            throw new IllegalArgumentException("Arbitrary repetition " + card + "allowed on triple constraints only.");
        }

    }

    @Override
    public void visit(TripleExprRef expr) {
        vCxt.getShapes().getTripleExpression(expr.getRef()).visit(this);
    }

    private boolean isEmptySubbag(Map<TripleConstraint, Integer> bag, TripleExpression expression,
                                  ValidationContext vCxt) {
        List<TripleConstraint> list = ShapeEval.findTripleConstraints(vCxt, expression);
        for (TripleConstraint tripleConstraint : list) {
            if (bag.get(tripleConstraint) != 0)
                return false;
        }
        return true;
    }

    private static Cardinality add (Cardinality i1, Cardinality i2) {
        int imin, imax;

        imin = i1.min + i2.min;
        imax = (i1.max == Cardinality.UNBOUNDED || i2.max == Cardinality.UNBOUNDED) ? Cardinality.UNBOUNDED : i1.max + i2.max;

        return new Cardinality(imin, imax);
    }

    private static Cardinality inter (Cardinality i1, Cardinality i2) {
        int imin, imax;

        imin = Math.max(i1.min, i2.min);
        imax = Math.min(i1.max, i2.max);

        return new Cardinality(imin, imax);
    }

    /** This function relies on the fact that the empty interval is represented by [2;1],
     * thus card.max() cannot be equal to 0 except if the interval is [0;0]
     *
     * @param nbOcc
     * @param card
     * @return
     */
    private static Cardinality div(int nbOcc, Cardinality card) {

        if (card.equals(ZERO))
            return nbOcc == 0 ? Cardinality.STAR : EMPTY;

        int imin, imax;

        // imin = nbOcc / card.imax();   uppper bound
        // with upper bound of (0 / UNBOUNDED) = 0
        // and  upper bound of (n / UNBOUNDED) = 1 for n != 0
        if (card.max == Cardinality.UNBOUNDED)
            imin = nbOcc == 0 ? 0 : 1;
        else
            imin = nbOcc % card.max == 0 ? nbOcc / card.max : (nbOcc / card.max) + 1;

        // imax = nbOcc / card.imin();  lower bound
        // with lower bound of (0 / 0)
        // and  lower bound of (n / 0) = UNBOUNDED for n != 0
        imax = card.min == 0 ? Cardinality.UNBOUNDED : nbOcc / card.min;

        return new Cardinality(imin,imax);
    }


}
