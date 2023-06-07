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

import org.apache.jena.shex.expressions.*;

class IntervalComputation implements TypedTripleExprVisitor<Cardinality> {

    /*package*/ static Cardinality ZERO_INTERVAL = new Cardinality(0, 0);
    /*package*/ static Cardinality EMPTY_INTERVAL = new Cardinality(2, 1);

    private final Bag bag;
    private final SorbeTripleExpr sorbeTripleExpr;

    public IntervalComputation(SorbeTripleExpr sorbeTripleExpr, Bag bag) {
        this.bag = bag;
        this.sorbeTripleExpr = sorbeTripleExpr;
    }

    @Override
    public Cardinality visit(TripleConstraint tripleConstraint) {
        int nbOcc = bag.getCard(tripleConstraint);
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

        if (card.equals(Cardinality.STAR))
            if (sorbeTripleExpr.isEmptySubbag(bag, tripleExprCardinality))
                return Cardinality.STAR;
            else {
                Cardinality subResult = subExpr.visit(this);
                return subResult.equals(EMPTY_INTERVAL) ? EMPTY_INTERVAL : Cardinality.PLUS;
            }
        if (card.equals(Cardinality.PLUS))
            if (sorbeTripleExpr.isEmptySubbag(bag, tripleExprCardinality))
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
            int nbOcc = bag.getCard((TripleConstraint) subExpr);
            return div(nbOcc, card);
        }
        if (card.equals(ZERO_INTERVAL))
            return sorbeTripleExpr.isEmptySubbag(bag, tripleExprCardinality) ? Cardinality.STAR : EMPTY_INTERVAL;

        throw new IllegalArgumentException("Arbitrary repetition " + card + "allowed on triple constraints only.");
    }

    @Override
    public Cardinality visit(TripleExprRef tripleExprRef) {
        throw new IllegalArgumentException("References not supported");
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
