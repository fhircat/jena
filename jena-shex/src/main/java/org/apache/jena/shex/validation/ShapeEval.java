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

import java.util.*;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.other.G;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.util.AccumulationUtil;
import org.apache.jena.util.iterator.ExtendedIterator;

public class ShapeEval {

    // TODO remove debug code
    static boolean DEBUG = false;
    static boolean DEBUG_eachOf = DEBUG;
    static boolean DEBUG_cardinalityOf = DEBUG;

    public static void debug(boolean debug) {
        DEBUG = debug;
        DEBUG_eachOf = debug;
        DEBUG_cardinalityOf = debug;
    }

    public static boolean matchesTripleExpr(Node dataNode, TripleExpr tripleExpr,
                                            Set<Node> extraPredicates, boolean closed,
                                            ValidationContext vCxt) {
        Pair<Set<Node>, Set<Node>> predicates = AccumulationUtil.collectPredicates(tripleExpr, vCxt::getTripleExpr);
        Set<Node> forwardPredicates  = predicates.getLeft();
        Set<Node> backwardPredicates = predicates.getRight();

        Set<Triple> accMatchables = new HashSet<>();
        Set<Triple> accNonMatchables = new HashSet<>();
        arcsOut(vCxt.getGraph(), dataNode, forwardPredicates, accMatchables, accNonMatchables);
        arcsIn(vCxt.getGraph(), dataNode, backwardPredicates, accMatchables);

        if (closed && ! accNonMatchables.isEmpty())
            return false;
        return  matchesExpr(accMatchables, tripleExpr, extraPredicates, vCxt);
    }

    private static boolean matchesExpr(Set<Triple> triples, TripleExpr tripleExpr, Set<Node> extraPredicates,
                                       ValidationContext vCxt) {

        SorbeTripleExpr sorbeTripleExpr = vCxt.getSorbeHandler().getSorbe(tripleExpr, vCxt.getSchema());

        // 1. Identify which triples could match which triple constraints
        Map<Triple, List<TripleConstraint>> preMatching = sorbeTripleExpr.getPredicateBasedPreMatching(triples);

        // 2. Recursively validate every pair (triple, tripleConstraint), while removing those that are not valid
        preMatching.forEach((triple, matchingTripleConstraints) -> {
            Iterator<TripleConstraint> it = matchingTripleConstraints.iterator();
            while (it.hasNext()) {
                TripleConstraint tc = it.next();
                ShapeExpr valueExpr = tc.getValueExpr();
                Node opposite = tc.isInverse() ? triple.getSubject() : triple.getObject();
                if (!ShapeExprEval.satisfies(valueExpr, opposite, vCxt))
                    it.remove();
            }
        });

        // 3. Check whether all non matching triples are allowed by extra
        Iterator<Map.Entry<Triple, List<TripleConstraint>>> it = preMatching.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Triple, List<TripleConstraint>> e = it.next();
            if (e.getValue().isEmpty()) {
                // the triple satisfies none of the triple constraints
                if (! extraPredicates.contains(e.getKey().getPredicate()))
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

            Cardinality interval = computeInterval(sorbeTripleExpr,
                    Bag.fromMatching(matching, sorbeTripleExpr.getAllSorbeTripleConstraints()));
            if (interval.min <= 1 && 1 <= interval.max) {
                // the triple expression is satisfied by the matching, check semantic actions
                if (sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                        .allMatch(p -> vCxt.dispatchTripleExprSemanticAction(p.getKey(), p.getValue())))
                    return true;
            }
        }
        return false;

    }

    private static void arcsOut(Graph graph, Node dataNode, Set<Node> predicates,
                                Set<Triple> accMatchables, Set<Triple> accNonMatchables) {
        ExtendedIterator<Triple> x = G.find(graph, dataNode, null, null);
        x.forEach(t -> {
            if (predicates.contains(t.getPredicate()))
                accMatchables.add(t);
            else
                accNonMatchables.add(t);
        });
    }

    private static void arcsIn(Graph graph, Node dataNode, Set<Node> predicates, Set<Triple> acc) {
        ExtendedIterator<Triple> x = G.find(graph, null, null, dataNode);
        x.filterKeep(t -> predicates.contains(t.getPredicate())).forEach(acc::add);
    }

    private static Cardinality computeInterval (SorbeTripleExpr sorbeTripleExpr, Bag bag) {
        IntervalComputation computation = new IntervalComputation(sorbeTripleExpr, bag);
        return sorbeTripleExpr.sorbe.visit(computation);
    }


}
