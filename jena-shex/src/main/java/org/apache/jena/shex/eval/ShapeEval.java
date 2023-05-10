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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        List<TripleConstraint> tripleConstraints = findTripleConstraints(vCxt, tripleExpr);
        Set<Node> forwardPredicates  = tripleConstraints.stream()
                .filter(tc -> ! tc.reverse()).map(TripleConstraint::getPredicate).collect(Collectors.toSet());
        Set<Node> backwardPredicates = tripleConstraints.stream()
                .filter(tc -> tc.reverse()).map(TripleConstraint::getPredicate).collect(Collectors.toSet());

        Set<Triple> matchables = new HashSet<>();
        Set<Triple> non_matchables = new HashSet<>();
        arcsOut(matchables, non_matchables, vCxt.getData(), node, forwardPredicates);
        arcsIn(matchables, vCxt.getData(), node, backwardPredicates);
        // TODO: pre-matching that computes matchables and non_matchables in one pass

        if (closed && ! non_matchables.isEmpty())
            return false;
        return  matches(vCxt, matchables, node, tripleExpr, extras);
    }

    static boolean matches(ValidationContext vCxt, Set<Triple> matchables, Node node,
                           TripleExpression tripleExpr, Set<Node> extras) {
        // TODO extras should never be null, modify this in Shape
        return matchesExpr(vCxt, matchables, node, tripleExpr, extras != null ? extras : Collections.emptySet());
    }

    private static boolean matchesExpr(ValidationContext vCxt, Set<Triple> triples, Node node,
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

            Cardinality interval = computeInterval(sorbeTripleExpr, matchingToBag(matching, tripleConstraints), vCxt);
            if (1 >= interval.min && 1 <= interval.max) {
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
        return triples.stream().collect(Collectors.toMap(Function.identity(),
                t -> new ArrayList<>(tcsByPredicate.get(t.getPredicate()))));
    }

    public static Map<TripleConstraint, Integer> matchingToBag (Map<Triple, TripleConstraint> matching,
                                                                List<TripleConstraint> base) {
        Map<TripleConstraint, Integer> bag = base.stream().collect(Collectors.toMap(Function.identity(), x->0));
        for (TripleConstraint tc : matching.values())
            bag.computeIfPresent(tc, (k, old) -> old+1);
        return bag;
    }


    private static boolean matchesTripleExprRef(ValidationContext vCxt, Set<Triple> matchables, Node node, TripleExprRef ref, Set<Node> extras) {
        Node label = ref.getRef();
        if ( label == null ) {}
        TripleExpression tripleExpr = vCxt.getTripleExpression(label);
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
                if ( expr.getRef() == null )
                    throw new ShexException("Failed to dereference : "+expr.getRef());
                shapes.getTripleExpression(expr.getRef()).visit(this);
            }

            @Override
            public void visit(TripleConstraint expr) {
                expr.visit(step);
            }
        };
    }

    /*package*/ static List<TripleConstraint> findTripleConstraints(ValidationContext vCxt,
                                                                    TripleExpression tripleExpr) {
        List<TripleConstraint> constraints = new ArrayList<>();
        tripleExpr.visit(accumulator(vCxt.getShapes(), constraints, Function.identity()));
        return constraints;
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

    private static void arcsOut(Set<Triple> matchables, Set<Triple> non_matchables, Graph graph, Node node, Set<Node> predicates) {
        ExtendedIterator<Triple> x = G.find(graph, node, null, null);
        x.forEach(t -> {
            if (predicates.contains(t.getPredicate()))
                matchables.add(t);
            else
                non_matchables.add(t);
        });
    }

    private static void arcsIn(Set<Triple> neigh, Graph graph, Node node, Set<Node> predicates) {
        ExtendedIterator<Triple> x = G.find(graph, null, null, node);
        x.filterKeep(t -> predicates.contains(t.getPredicate())).forEach(neigh::add);
    }

    private static Cardinality computeInterval (TripleExpression tripleExpr, Map<TripleConstraint, Integer> bag,
                                                ValidationContext vCxt) {
        IntervalComputation computation = new IntervalComputation(bag, vCxt);
        tripleExpr.visit(computation);
        return computation.getResult();
    }

}
