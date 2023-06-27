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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.other.G;
import org.apache.jena.shex.calc.Util;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.calc.AccumulationUtil;
import org.apache.jena.util.iterator.ExtendedIterator;

public class TripleExprEval {

    // TODO remove debug code
    static boolean DEBUG = false;
    static boolean DEBUG_eachOf = DEBUG;
    static boolean DEBUG_cardinalityOf = DEBUG;

    public static void debug(boolean debug) {
        DEBUG = debug;
        DEBUG_eachOf = debug;
        DEBUG_cardinalityOf = debug;
    }

    /*package*/ static boolean matchesExpr(Set<Triple> triples, TripleExpr tripleExpr, Set<Node> extraPredicates,
                               List<Shape> allBaseShapes, ValidationContext vCxt) {
        List<SorbeTripleExpr> baseTripleExprs = allBaseShapes.stream()
                .map(shape -> vCxt.getSorbe(shape.getTripleExpr()))
                .collect(Collectors.toList());
        return matchesExprNew(triples, extraPredicates, baseTripleExprs, vCxt);
    }

    // TODO note: starting to generalize for extends.
    private static boolean matchesExprNew (Set<Triple> triples,
                                           Set<Node> extraPredicates,
                                           List<SorbeTripleExpr> baseTripleExprs,
                                           ValidationContext vCxt) {

        // 1. Identify which triples could match which triple constraints
        Map<Triple, List<TripleConstraint>> preMatching = triples.stream()
                .collect(Collectors.toMap(Function.identity(),
                    t -> new ArrayList<>()));
        for (SorbeTripleExpr sorbeTripleExpr :  baseTripleExprs) {
            Map<Triple, List<TripleConstraint>> pm = sorbeTripleExpr.getPredicateBasedPreMatching(triples);
            pm.forEach((triple, list) -> preMatching.get(triple).addAll(list));
        }

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
            boolean mainShapesAreSatisfied = baseTripleExprs.stream().allMatch(sorbeTripleExpr -> {
                Cardinality interval = sorbeTripleExpr.computeInterval(matching);
                return interval.min <= 1 && 1 <= interval.max
                        // the triple expression is satisfied by the matching, check semantic actions
                        &&
                        sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                                .allMatch(p -> vCxt.dispatchTripleExprSemanticAction(p.getKey(), p.getValue()));
            });
            if (mainShapesAreSatisfied)
                // TODO verify satisfaction of the constraints
                return true;
        }
        return false;
    }

    private static boolean matchesExprOld(Set<Triple> triples, TripleExpr tripleExpr, Set<Node> extraPredicates,
                                          ValidationContext vCxt) {

        SorbeTripleExpr sorbeTripleExpr = vCxt.getSorbe(tripleExpr);

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
            Cardinality interval = sorbeTripleExpr.computeInterval(matching);
            if (interval.min <= 1 && 1 <= interval.max
                    // the triple expression is satisfied by the matching, check semantic actions
                    &&
                    sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                            .allMatch(p -> vCxt.dispatchTripleExprSemanticAction(p.getKey(), p.getValue())))
                return true;
        }
        return false;
    }

    /*package*/ static boolean matchesExtendsConstraintTripleExpr (Set<Triple> triples,
                                                                   TripleExpr tripleExpr,
                                                                   ValidationContext vCxt) {
        // Extra and closed are ignored
        SorbeTripleExpr sorbeTripleExpr = vCxt.getSorbe(tripleExpr);

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

        // 3. Check that there are no non-matching triples
        if (preMatching.values().stream().anyMatch(List::isEmpty))
            return false;

        // 4. SORBE based validation algorithm on the matching triples
        Iterator<Map<Triple, TripleConstraint>> mit = new MatchingsIterator(preMatching, new ArrayList<>(preMatching.keySet()));
        while (mit.hasNext()) {
            Map<Triple, TripleConstraint> matching = mit.next();
            Cardinality interval = sorbeTripleExpr.computeInterval(matching);
            if (interval.min <= 1 && 1 <= interval.max
                    // the triple expression is satisfied by the matching, check semantic actions
                    // TODO do we ignore semantic actions in this case ?
                    &&
                    sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                            .allMatch(p -> vCxt.dispatchTripleExprSemanticAction(p.getKey(), p.getValue())))
                return true;
        }
        return false;



    }


}
