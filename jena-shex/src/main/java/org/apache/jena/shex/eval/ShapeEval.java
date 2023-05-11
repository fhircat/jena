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

import static org.apache.jena.atlas.lib.StreamOps.toSet;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.jena.atlas.lib.NotImplemented;
import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.other.G;
import org.apache.jena.shex.ShexException;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.sys.ReportItem;
import org.apache.jena.shex.sys.ValidationContext;
import org.apache.jena.util.iterator.ExtendedIterator;

public class ShapeEval {

    static boolean DEBUG = false;
    static boolean DEBUG_eachOf = DEBUG;
    static boolean DEBUG_cardinalityOf = DEBUG;

    public static void debug(boolean debug) {
        DEBUG = debug;
        DEBUG_eachOf = debug;
        DEBUG_cardinalityOf = debug;
    }

    // With help from the ideas (not code) of:
    // https://github.com/hsolbrig/PyShEx/blob/master/pyshex/shape_expressions_language/p5_5_shapes_and_triple_expressions.py

    public static boolean matchesShapeExpr(ValidationContext vCxt, ShapeExpr shapeExpr, Node node) {
        return shapeExpr.satisfies(vCxt, node);
    }

    /*package*/ public static boolean matchesTripleExpr(ValidationContext vCxt, TripleExpression tripleExpr,
                                                        Node node, Set<Node> extras, boolean closed) {
//        Set<Triple> neigh = new HashSet<>();
//        Set<Triple> arcsOut = new HashSet<>();
//        Set<Triple> arcsIn = new HashSet<>();
//        arcsOut(arcsOut, vCxt.getData(), node);
//        arcsIn(arcsIn, vCxt.getData(), node);
//        neigh.addAll(arcsOut);
//        neigh.addAll(arcsIn);

        Set<Triple> arcsOut = new HashSet<>();
        arcsOut(arcsOut, vCxt.getData(), node);
        Set<Node> predicates = new HashSet<>(findPredicates(vCxt, tripleExpr));
        // TODO: pre-matching that computes matchables and non_matchables in one pass
        // TODO: arcs in and inverse properties ?
        Set<Triple> matchables = toSet(arcsOut.stream().filter(t->predicates.contains(t.getPredicate())));

        boolean b = matches(vCxt, matchables, node, tripleExpr, extras);
        if ( ! b )
            return false;
        if ( closed ) { // TODO moved the closed condition before, as it could return avoid unnecessary and costly calculation
            // CLOSED : no other triples.
            Set<Triple> non_matchables = toSet(arcsOut.stream().filter(t->!matchables.contains(t)));
            if ( ! non_matchables.isEmpty() )
                return false;
        }
        return true;
    }

    static boolean matches(ValidationContext vCxt, Set<Triple> matchables, Node node,
                           TripleExpression tripleExpr, Set<Node> extras) {
        // TODO extras should never be null, modify this in Shape
        return matchesExpr2(vCxt, matchables, node, tripleExpr, extras != null ? extras : Collections.emptySet());
    }

    private static boolean matchesExpr2(ValidationContext vCxt, Set<Triple> triples, Node node,
                                       TripleExpression tripleExpr, Set<Node> extras) {

        TripleExpression sorbeTripleExpr = getSorbe(tripleExpr);
        List<TripleConstraint> tripleConstraints = findTripleConstraints(vCxt, sorbeTripleExpr);

        // 1. Identify which triples could match which triple constraints
        Map<Triple, List<TripleConstraint>> preMatching = computePredicateBasedPreMatching(triples, tripleConstraints);

        // 2. Recursively validate every pair (triple, tripleConstraint), while removing those that are not valid
        for (Map.Entry<Triple, List<TripleConstraint>> e : preMatching.entrySet()) {
            Triple triple = e.getKey();
            List<TripleConstraint> tcs = e.getValue();
            Iterator<TripleConstraint> it = tcs.iterator();
            while (it.hasNext()) {
                TripleConstraint tc = it.next();
                ShapeExpr valueExpr = tc.getShapeExpression();
                Node opposite = tc.reverse() ? triple.getSubject() : triple.getObject();
                if (! valueExpr.satisfies(vCxt, opposite))
                    it.remove();
            }
        }

        // 3. Check whether all non matching triples are allowed by extra
        Iterator<Map.Entry<Triple, List<TripleConstraint>>> it = preMatching.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Triple, List<TripleConstraint>> e = it.next();
            if (e.getValue().isEmpty()) {
                // the triple satisfies none of the triple constraints
                if (! extras.contains(e.getKey().getPredicate()))
                    // should satisfy extra
                    return false;
                // remove the triple as it does not participate in the satisfaction of the triple expression
                it.remove();
            }
        }

        // 4. SORBE based validation algorithm on the matching triples
        Iterator<Map<Triple, TripleConstraint>> mit = new MatchingsIterator(preMatching, new ArrayList<>(preMatching.keySet()));
        while (mit.hasNext()) {
            Map<Triple, TripleConstraint> matching = mit.next();

            Interval interval = computeInterval(sorbeTripleExpr, matchingToBag(matching, tripleConstraints));
            if (interval.contains(1)) {
                return true;
            }
        }
        return false;

    }

    private static TripleExpression getSorbe(TripleExpression tripleExpr) {
        // FIXME compute the real sorbe
        return tripleExpr;
    }

    private static Map<Triple, List<TripleConstraint>> computePredicateBasedPreMatching(Collection<Triple> triples,
                                                                              List<TripleConstraint> tripleConstraints) {
        Map<Node, List<TripleConstraint>> tcsByPredicate = tripleConstraints.stream()
                .collect(Collectors.groupingBy(TripleConstraint::getPredicate)); // TODO EFFICIENCY can be pre-computed for every sorbe expression
        return triples.stream().collect(Collectors.toMap(Function.identity(), t -> tcsByPredicate.get(t.getPredicate())));
    }

    public static Map<TripleConstraint, Integer> matchingToBag (Map<Triple, TripleConstraint> matching,
                                                                List<TripleConstraint> base) {
        Map<TripleConstraint, Integer> bag = base.stream().collect(Collectors.toMap(Function.identity(), x->0));
        for (TripleConstraint tc : matching.values())
            bag.computeIfPresent(tc, (k, old) -> old+1);
        return bag;
    }


    private static boolean matchesExpr(ValidationContext vCxt, Set<Triple> T, Node node,
                                       TripleExpression tripleExpr, Set<Node> extras) {

        if ( tripleExpr instanceof EachOf) {
            return ShapeEvalEachOf.matchesEachOf(vCxt, T, node, (EachOf)tripleExpr, extras);
        }
        else if ( tripleExpr instanceof OneOf) {
            return ShapeEvalOneOf.matchesOneOf(vCxt, T, node, (OneOf)tripleExpr, extras);
        }
        else if ( tripleExpr instanceof TripleExprRef ) {
            return matchesTripleExprRef(vCxt, T, node, (TripleExprRef)tripleExpr, extras);
        }
        else if ( tripleExpr instanceof TripleExprCardinality ) {
            return ShapeEvalCardinality.matchesCardinality(vCxt, T, node, (TripleExprCardinality)tripleExpr, extras);
        }
        else if ( tripleExpr instanceof TripleConstraint ) {
            return ShapeEvalTripleConstraint.matchesCardinalityTC(vCxt, T, node, (TripleConstraint)tripleExpr, extras);
        }
        else if ( tripleExpr instanceof TripleExprEmpty) {
            return true;
        }
        throw new NotImplemented(tripleExpr.getClass().getSimpleName());
    }

    private static boolean matchesTripleExprRef(ValidationContext vCxt, Set<Triple> matchables, Node node, TripleExprRef ref, Set<Node> extras) {
        Node label = ref.ref();
        if ( label == null ) {}
        TripleExpression tripleExpr = vCxt.getTripleExpr(label);
        if ( tripleExpr == null ) {
            ReportItem rItem = new ReportItem("Failed to get triple expression from reference", label);
            vCxt.reportEntry(rItem);
        }
        return matches(vCxt, matchables, node, tripleExpr, extras);
    }

    // Recursive.
    private static TripleExprVisitor walk(ShexSchema shapes, TripleExprVisitor step) {
        //Walker
        return new TripleExprVisitor() {
            @Override
            public void visit(TripleExprCardinality expr) {
                expr.visit(step);
                expr.target().visit(this);
            }

            @Override
            public void visit(EachOf expr) {
                expr.visit(step);
                expr.expressions().forEach(ex -> ex.visit(this));
            }

            @Override
            public void visit(OneOf expr) {
                expr.visit(step);
                expr.expressions().forEach(ex -> ex.visit(this));
            }

            @Override
            public void visit(TripleExprEmpty expr) {
                expr.visit(step);
            }

            @Override
            public void visit(TripleExprRef expr) {
                expr.visit(step);
                if ( expr.ref() == null )
                    throw new ShexException("Failed to dereference : "+expr.ref());
                shapes.getTripleExpression(expr.ref()).visit(this);
            }

            @Override
            public void visit(TripleConstraint expr) {
                expr.visit(step);
            }
        };
    }

    /*package*/ static List<TripleConstraint> findTripleConstraints(ValidationContext vCxt, TripleExpression tripleExpr) {
        List<TripleConstraint> constraints = new ArrayList<>();
        tripleExpr.visit(accumulator(vCxt.getShapes(), constraints, Function.identity()));
        return constraints;
    }

    /*package*/ static List<Node> findPredicates(ValidationContext vCxt, TripleExpression tripleExpr) {
        List<Node> predicates = new ArrayList<>();
        tripleExpr.visit(accumulator(vCxt.getShapes(), predicates, TripleConstraint::getPredicate));
        return predicates;
    }

    private static <X> TripleExprVisitor accumulator(ShexSchema shapes, Collection<X> acc,
                                                     Function<TripleConstraint, X> mapper) {
        TripleExprVisitor step = new TripleExprVisitor() {
            @Override
            public void visit(TripleConstraint tripleConstraint) {
                acc.add(mapper.apply(tripleConstraint));
            }
        };
        return walk(shapes, step);
    }

    private static void arcsOut(Set<Triple> neigh, Graph graph, Node node) {
        ExtendedIterator<Triple> x = G.find(graph, node, null, null);
        x.forEach(neigh::add);
    }

    private static void arcsIn(Set<Triple> neigh, Graph graph, Node node) {
        ExtendedIterator<Triple> x = G.find(graph, null, null, node);
        x.forEach(neigh::add);
    }

    private static Interval computeInterval (TripleExpression tripleExpr, Map<TripleConstraint, Integer> bag) {
        IntervalComputation computation = new IntervalComputation(bag);
        tripleExpr.visit(computation);
        return computation.getResult();
    }

    private static class IntervalComputation implements TripleExprVisitor {
        private final Map<TripleConstraint, Integer> bag;
        Interval result;

        public IntervalComputation(Map<TripleConstraint, Integer> bag) {
            this.bag = bag;
            result = null;
        }

        private void setResult (Interval result) {
            this.result = result;
        }

        private Interval getResult() {
            return this.result;
        }

        @Override
        public void visit(TripleConstraint tc) {

            int nbOcc = bag.get(tc);
            setResult(new Interval(nbOcc, nbOcc));
        }

        @Override
        public void visit(TripleExprEmpty emptyTripleExpression) {
            setResult(Interval.STAR);
        }

        @Override
        public void visit(OneOf expr) {
            Interval res = Interval.ZERO; // the neutral element for addition

            for (TripleExpression subExpr : expr.expressions()) {
                subExpr.visit(this);
                res = Interval.add(res, getResult());
            }
            setResult(res);
        }

        @Override
        public void visit(EachOf expr) {
            Interval res = Interval.STAR; // the neutral element for intersection

            for (TripleExpression subExpr : expr.expressions()) {
                subExpr.visit(this);
                res = Interval.inter(res, getResult());
            }
            setResult(res);
        }

        @Override
        public void visit(TripleExprCardinality expression) {

            Interval card = new Interval(expression.min(), expression.max());
            TripleExpression subExpr = expression.target();
            boolean isEmptySubbag = isEmptySubbag(bag, expression);

            if (card.equals(Interval.STAR)) {
                if (isEmptySubbag) {
                    setResult(Interval.STAR);
                } else {
                    subExpr.visit(this);
                    if (! this.getResult().equals(Interval.EMPTY)) {
                        setResult(Interval.PLUS);
                    }
                }
            }

            else if (card.equals(Interval.PLUS)) {
                if (isEmptySubbag) {
                    setResult(Interval.ZERO);
                } else {
                    subExpr.visit(this);
                    if (! this.getResult().equals(Interval.EMPTY)) {
                        setResult(new Interval(1, getResult().max));
                    } else {
                        setResult(Interval.EMPTY);
                    }
                }
            }

            else if (card.equals(Interval.OPT)) {
                subExpr.visit(this);
                setResult(Interval.add(getResult(), Interval.STAR));
            }

            else if (subExpr instanceof TripleConstraint) {
                int nbOcc = bag.get((TripleConstraint)  subExpr);
                setResult(Interval.div(nbOcc, card));
            }

            else if (card.equals(Interval.ZERO)) {
                if (isEmptySubbag) {
                    setResult(Interval.STAR);
                } else {
                    setResult(Interval.EMPTY);
                }
            }

            else {
                throw new IllegalArgumentException("Arbitrary repetition " + card + "allowed on triple constraints only.");
            }

        }

        @Override
        public void visit(TripleExprRef expr) {
            expr.getTarget().visit(this);
        }

        private boolean isEmptySubbag(Map<TripleConstraint, Integer> bag, TripleExpression expression){
            List<TripleConstraint> list = findTripleConstraints(null, expression);
            for(TripleConstraint tripleConstraint : list){
                if(bag.get(tripleConstraint) != 0)
                    return false;
            }
            return true;
        }

    }
}
