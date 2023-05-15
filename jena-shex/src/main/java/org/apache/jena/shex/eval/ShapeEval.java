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

    /*package*/ public static boolean matchesTripleExpr(ValidationContext vCxt, TripleExpr tripleExpr,
                                                        Node node, Set<Node> extras, boolean closed) {
        List<TripleConstraint> tripleConstraints = Util.collectTripleConstraints(tripleExpr,
                true, vCxt.getShapes());
        Set<Node> forwardPredicates  = tripleConstraints.stream()
                .filter(tc -> ! tc.isInverse()).map(TripleConstraint::getPredicate).collect(Collectors.toSet());
        Set<Node> backwardPredicates = tripleConstraints.stream()
                .filter(tc -> tc.isInverse()).map(TripleConstraint::getPredicate).collect(Collectors.toSet());

        Set<Triple> matchables = new HashSet<>();
        Set<Triple> non_matchables = new HashSet<>();
        arcsOut(matchables, non_matchables, vCxt.getData(), node, forwardPredicates);
        arcsIn(matchables, vCxt.getData(), node, backwardPredicates);
        // TODO: pre-matching that computes matchables and non_matchables in one pass

        if (closed && ! non_matchables.isEmpty())
            return false;
        return  matchesExpr(vCxt, matchables, node, tripleExpr, extras != null ? extras : Collections.emptySet());
    }

    private static boolean matchesExpr(ValidationContext vCxt, Set<Triple> triples, Node node,
                                       TripleExpr tripleExpr, Set<Node> extras) {

        SorbeTripleExpr sorbeTripleExpr = getSorbe(tripleExpr, vCxt);
        List<TripleConstraint> tripleConstraints = Util.collectTripleConstraints(sorbeTripleExpr.sorbe,
                false, null);

        // 1. Identify which triples could match which triple constraints
        Map<Triple, List<TripleConstraint>> preMatching = computePredicateBasedPreMatching(triples, tripleConstraints);

        // 2. Recursively validate every pair (triple, tripleConstraint), while removing those that are not valid
        for (Map.Entry<Triple, List<TripleConstraint>> e : preMatching.entrySet()) {
            Triple triple = e.getKey();
            List<TripleConstraint> tcs = e.getValue();
            Iterator<TripleConstraint> it = tcs.iterator();
            while (it.hasNext()) {
                TripleConstraint tc = it.next();
                ShapeExpr valueExpr = tc.getValueExpr();
                Node opposite = tc.isInverse() ? triple.getSubject() : triple.getObject();
                if (! ShapeExprEval.satisfies(valueExpr, opposite, vCxt))
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
                // remove the triple as it should not participate in the satisfaction of the triple expression
                it.remove();
            }
        }

        // 4. SORBE based validation algorithm on the matching triples
        Iterator<Map<Triple, TripleConstraint>> mit = new MatchingsIterator(preMatching, new ArrayList<>(preMatching.keySet()));
        while (mit.hasNext()) {
            Map<Triple, TripleConstraint> matching = mit.next();

            Cardinality interval = computeInterval(sorbeTripleExpr.sorbe, matchingToBag(matching, tripleConstraints), vCxt);
            if (interval.min <= 1 && 1 <= interval.max) {
                boolean allSemActsSatisfied = true;
                // the triple expression is satisfied by the matching, check semantic actions
                for (TripleExpr subExpr : sorbeTripleExpr.getSubExprsWithSemActs()) {
                    List<TripleConstraint> tripleConstraintsForSemAct = sorbeTripleExpr.sorbeTripleConstraints(subExpr, vCxt);
                    Set<Triple> matchedTriples = matching.entrySet().stream()
                            .filter(e -> tripleConstraintsForSemAct.contains(e.getValue()))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet());
                    allSemActsSatisfied &= subExpr.testSemanticActions(vCxt, matchedTriples);
                }
                if (allSemActsSatisfied)
                    return true;
            }
        }
        return false;

    }

    private static SorbeTripleExpr getSorbe(TripleExpr tripleExpr, ValidationContext vCxt) {
        return SorbeTripleExpr.create(tripleExpr, vCxt.getShapes());
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

    // Recursive.
    private static TripleExprVisitor walker(ShexSchema shapes, TripleExprVisitor step) {
        //Walker
        return new TripleExprVisitor() {
            @Override
            public void visit(TripleExprCardinality tripleExprCardinality) {
                tripleExprCardinality.visit(step);
                tripleExprCardinality.getSubExpr().visit(this);
            }

            @Override
            public void visit(EachOf eachOf) {
                eachOf.visit(step);
                eachOf.getTripleExprs().forEach(ex -> ex.visit(this));
            }

            @Override
            public void visit(OneOf oneOf) {
                oneOf.visit(step);
                oneOf.getTripleExprs().forEach(ex -> ex.visit(this));
            }

            @Override
            public void visit(TripleExprEmpty tripleExprEmpty) {
                tripleExprEmpty.visit(step);
            }

            @Override
            public void visit(TripleExprRef tripleExprRef) {
                tripleExprRef.visit(step);
                if ( tripleExprRef.getLabel() == null )
                    throw new ShexException("Failed to dereference : "+ tripleExprRef.getLabel());
                shapes.getTripleExpression(tripleExprRef.getLabel()).visit(this);
            }

            @Override
            public void visit(TripleConstraint tripleConstraint) {
                tripleConstraint.visit(step);
            }
        };
    }

//
//    /*package*/ static List<TripleConstraint> findTripleConstraints(ValidationContext vCxt,
//                                                                    TripleExpr tripleExpr) {
//        List<TripleConstraint> constraints = new ArrayList<>();
//        tripleExpr.visit(accumulator(vCxt.getShapes(), constraints, Function.identity()));
//        return constraints;
//    }

//    private static <X> TripleExprVisitor accumulator(ShexSchema shapes, Collection<X> acc,
//                                                     Function<TripleConstraint, X> mapper) {
//        TripleExprVisitor step = new TripleExprVisitor() {
//            @Override
//            public void visit(TripleConstraint tripleConstraint) {
//                    acc.add(mapper.apply(tripleConstraint));
//            }
//        };
//        return walker(shapes, step);
//    }

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

    private static Cardinality computeInterval (TripleExpr tripleExpr, Map<TripleConstraint, Integer> bag,
                                                ValidationContext vCxt) {
        IntervalComputation computation = new IntervalComputation(bag, vCxt);
        return tripleExpr.visit(computation);
    }

}
