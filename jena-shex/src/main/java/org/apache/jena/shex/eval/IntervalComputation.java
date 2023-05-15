package org.apache.jena.shex.eval;

import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.sys.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class IntervalComputation implements TripleExprVisitor2<Cardinality> {

    /*package*/ static Cardinality ZERO_INTERVAL = new Cardinality(0, 0);
    /*package*/ static Cardinality EMPTY_INTERVAL = new Cardinality(2, 1);

    private final Map<TripleConstraint, Integer> bag;
    private final ValidationContext vCxt;

    public IntervalComputation(Map<TripleConstraint, Integer> bag, ValidationContext vCxt) {
        this.bag = bag;
        this.vCxt = vCxt;
    }

    @Override
    public Cardinality visit(TripleConstraint tripleConstraint) {
        int nbOcc = bag.get(tripleConstraint);
        return new Cardinality(nbOcc, nbOcc);
    }

    @Override
    public Cardinality visit(TripleExprEmpty tripleExprEmpty) {
        return Cardinality.STAR;
    }

    @Override
    public Cardinality visit(OneOf oneOf) {
        Cardinality res = ZERO_INTERVAL; // the neutral element for addition

        for (TripleExpr subExpr : oneOf.getTripleExprs())
            res = add(res, subExpr.visit(this));
        return res;
    }

    @Override
    public Cardinality visit(EachOf eachOf) {
        Cardinality res = Cardinality.STAR; // the neutral element for intersection

        for (TripleExpr subExpr : eachOf.getTripleExprs())
            res = inter(res, subExpr.visit(this));
        return res;
    }

    @Override
    public Cardinality visit(TripleExprCardinality tripleExprCardinality) {

        Cardinality card = new Cardinality(tripleExprCardinality.min(), tripleExprCardinality.max());
        TripleExpr subExpr = tripleExprCardinality.getSubExpr();
        boolean isEmptySubbag = isEmptySubbag(bag, tripleExprCardinality, vCxt);

        if (card.equals(Cardinality.STAR))
            if (isEmptySubbag)
                return Cardinality.STAR;
            else {
                Cardinality subResult = subExpr.visit(this);
                return subResult.equals(EMPTY_INTERVAL) ? EMPTY_INTERVAL : Cardinality.PLUS;
            }
        if (card.equals(Cardinality.PLUS))
            if (isEmptySubbag)
                return ZERO_INTERVAL;
            else {
                Cardinality subResult = subExpr.visit(this);
                return subResult.equals(EMPTY_INTERVAL) ? EMPTY_INTERVAL : new Cardinality(1, subResult.max);
            }
        if (card.equals(Cardinality.OPT)) {
            Cardinality subResult = subExpr.visit(this);
            return add(subResult, Cardinality.STAR);
        }
        if (subExpr instanceof TripleConstraint) {
            int nbOcc = bag.get((TripleConstraint) subExpr);
            return div(nbOcc, card);
        }
        if (card.equals(ZERO_INTERVAL))
            return isEmptySubbag ? Cardinality.STAR : EMPTY_INTERVAL;

        throw new IllegalArgumentException("Arbitrary repetition " + card + "allowed on triple constraints only.");
    }


    @Override
    public Cardinality visit(TripleExprRef tripleExprRef) {
        return vCxt.getShapes().getTripleExpression(tripleExprRef.getLabel()).visit(this);
    }

    private boolean isEmptySubbag(Map<TripleConstraint, Integer> bag, TripleExpr expression,
                                  ValidationContext vCxt) {
        // TODO lazy computation
        List<TripleConstraint> list = Util.collectTripleConstraints(expression, false, null);
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

        if (card.equals(ZERO_INTERVAL))
            return nbOcc == 0 ? Cardinality.STAR : EMPTY_INTERVAL;

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
