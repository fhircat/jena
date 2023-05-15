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

import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.sys.ValidationContext;

import java.util.List;
import java.util.Map;

class IntervalComputation implements TripleExprVisitor {

    /*package*/ static Cardinality ZERO_INTERVAL = new Cardinality(0, 0);
    /*package*/ static Cardinality EMPTY_INTERVAL = new Cardinality(2, 1);

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
    public void visit(TripleConstraint tripleConstraint) {

        int nbOcc = bag.get(tripleConstraint);
        setResult(new Cardinality(nbOcc, nbOcc));
    }

    @Override
    public void visit(TripleExprEmpty tripleExprEmpty) {
        setResult(Cardinality.STAR);
    }

    @Override
    public void visit(OneOf oneOf) {
        Cardinality res = ZERO_INTERVAL; // the neutral element for addition

        for (TripleExpr subExpr : oneOf.getTripleExprs()) {
            subExpr.visit(this);
            res = add(res, getResult());
        }
        setResult(res);
    }

    @Override
    public void visit(EachOf eachOf) {
        Cardinality res = Cardinality.STAR; // the neutral element for intersection

        for (TripleExpr subExpr : eachOf.getTripleExprs()) {
            subExpr.visit(this);
            res = inter(res, getResult());
        }
        setResult(res);
    }

    @Override
    public void visit(TripleExprCardinality tripleExprCardinality) {

        Cardinality card = new Cardinality(tripleExprCardinality.min(), tripleExprCardinality.max());
        TripleExpr subExpr = tripleExprCardinality.getSubExpr();
        boolean isEmptySubbag = isEmptySubbag(bag, tripleExprCardinality, vCxt);

        if (card.equals(Cardinality.STAR)) {
            if (isEmptySubbag) {
                setResult(Cardinality.STAR);
            } else {
                subExpr.visit(this);
                if (!this.getResult().equals(EMPTY_INTERVAL)) {
                    setResult(Cardinality.PLUS);
                }
            }
        } else if (card.equals(Cardinality.PLUS)) {
            if (isEmptySubbag) {
                setResult(ZERO_INTERVAL);
            } else {
                subExpr.visit(this);
                if (!this.getResult().equals(EMPTY_INTERVAL)) {
                    setResult(new Cardinality(1, getResult().max));
                } else {
                    setResult(EMPTY_INTERVAL);
                }
            }
        } else if (card.equals(Cardinality.OPT)) {
            subExpr.visit(this);
            setResult(add(getResult(), Cardinality.STAR));
        } else if (subExpr instanceof TripleConstraint) {
            int nbOcc = bag.get((TripleConstraint) subExpr);
            setResult(div(nbOcc, card));
        } else if (card.equals(ZERO_INTERVAL)) {
            if (isEmptySubbag) {
                setResult(Cardinality.STAR);
            } else {
                setResult(EMPTY_INTERVAL);
            }
        } else {
            throw new IllegalArgumentException("Arbitrary repetition " + card + "allowed on triple constraints only.");
        }

    }

    @Override
    public void visit(TripleExprRef tripleExprRef) {
        vCxt.getShapes().getTripleExpression(tripleExprRef.getLabel()).visit(this);
    }

    private boolean isEmptySubbag(Map<TripleConstraint, Integer> bag, TripleExpr expression,
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
