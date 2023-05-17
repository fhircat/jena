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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.other.G;
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
        Pair<Set<Node>, Set<Node>> predicates = collectPredicates(tripleExpr, vCxt.getShapes());
        Set<Node> forwardPredicates  = predicates.getLeft();
        Set<Node> backwardPredicates = predicates.getRight();

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

        SorbeTripleExpr sorbeTripleExpr = SorbeTripleExpr.create(tripleExpr, vCxt.getShapes());
        List<TripleConstraint> tripleConstraints = sorbeTripleExpr.getAllSorbeTripleConstraints();

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

            Cardinality interval = computeInterval(sorbeTripleExpr, matchingToBag(matching, tripleConstraints), vCxt);
            if (interval.min <= 1 && 1 <= interval.max) {
                // the triple expression is satisfied by the matching, check semantic actions
                if (sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                        .allMatch(p -> p.getKey().testSemanticActions(vCxt, p.getValue())))
                    return true;
            }
        }
        return false;

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

    private static Cardinality computeInterval (SorbeTripleExpr sorbeTripleExpr, Map<TripleConstraint, Integer> bag,
                                                ValidationContext vCxt) {
        IntervalComputation computation = new IntervalComputation(sorbeTripleExpr, bag, vCxt);
        return sorbeTripleExpr.sorbe.visit(computation);
    }

    private static Pair<Set<Node>, Set<Node>> collectPredicates (TripleExpr tripleExpr, ShexSchema schema) {

        Set<Node> fwdPredicates = new HashSet<>();
        Set<Node> invPredicates = new HashSet<>();
        TripleExprAccumulationVisitor<Node> fwdPredAccumulator = new TripleExprAccumulationVisitor<>(fwdPredicates) {
            @Override
            public void visit(TripleConstraint tripleConstraint) {
                if (! tripleConstraint.isInverse())
                    accumulate(tripleConstraint.getPredicate());
            }
        };
        TripleExprAccumulationVisitor<Node> invPredaccumulator = new TripleExprAccumulationVisitor<>(invPredicates) {
            @Override
            public void visit(TripleConstraint tripleConstraint) {
                if (tripleConstraint.isInverse())
                    accumulate(tripleConstraint.getPredicate());
                super.visit(tripleConstraint);
            }
        };

        VoidWalker walker = new VoidWalker.Builder()
                .processTripleExprsWith(fwdPredAccumulator)
                .processTripleExprsWith(invPredaccumulator)
                .followTripleExprRefs(schema)
                .build();
        tripleExpr.visit(walker);
        return new ImmutablePair<>(fwdPredicates, invPredicates);
    }

}
