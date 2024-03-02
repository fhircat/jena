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

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /*package*/ static boolean matchesExpr(Set<Triple> triples,
                                           Shape shape,
                                           ValidationContext vCxt) {

        return null != matchesExprNew(triples, shape.getExtras(), Set.of(vCxt.getSorbe(shape.getTripleExpr())), vCxt);
    }

    /*package*/ static Map<Node, Set<Triple>> matchesExpr(Set<Triple> triples,
                                                          Shape shape,
                                                          Map<Node, Shape> baseMainShapes,
                                                          ValidationContext vCxt) {

        Map<Node, SorbeTripleExpr> correspondingSorbe = baseMainShapes.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> vCxt.getSorbe(e.getValue().getTripleExpr())));

        Map<Triple, TripleConstraint> satisfyingMatching = matchesExprNew(triples, shape.getExtras(),
                correspondingSorbe.values(), vCxt);

        if (null != satisfyingMatching)
            return groupByLabel(correspondingSorbe, satisfyingMatching);
        else
            return null;
    }

    private static Map<Triple, TripleConstraint> matchesExprNew (Set<Triple> triples,
                                                                 Set<Node> extraPredicates,
                                                                 Collection<SorbeTripleExpr> toBeMatched,
                                                                 ValidationContext vCxt) {

        // 1. With every triple, associate all the triple constraints that this triple could match
        Map<Triple, List<TripleConstraint>> preMatching = triples.stream()
                .collect(Collectors.toMap(Function.identity(),
                        t -> new ArrayList<>()));
        for (SorbeTripleExpr sorbeTripleExpr : toBeMatched) {
            // this loop is needed only for extends, but does no harm w/o extends
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
            }});

        // 3. Check whether all non matching triples are allowed by extra
        Iterator<Map.Entry<Triple, List<TripleConstraint>>> it = preMatching.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Triple, List<TripleConstraint>> e = it.next();
            if (e.getValue().isEmpty()) {
                // the triple satisfies none of the triple constraints
                if (! extraPredicates.contains(e.getKey().getPredicate()))
                    // should satisfy extra
                    return null;
                // remove the triple as it should not participate in the satisfaction of the triple expression
                it.remove();
            }
        }

        // 4. SORBE based validation algorithm on the matching triples
        Iterator<Map<Triple, TripleConstraint>> mit = new MatchingsIterator(preMatching, new ArrayList<>(preMatching.keySet()));
        while (mit.hasNext()) {
            Map<Triple, TripleConstraint> matching = mit.next();
            boolean mainShapesAreSatisfied = toBeMatched.stream().allMatch(sorbeTripleExpr -> {
                // this loop is needed only for extends, but does no harm w/o extends
                Cardinality interval = sorbeTripleExpr.computeInterval(matching);
                return interval.min <= 1 && 1 <= interval.max
                        // the triple expression is satisfied by the matching, check semantic actions
                        &&
                        sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                                .allMatch(p -> vCxt.dispatchTripleExprSemanticAction(p.getKey(), p.getValue()));
            });
            if (mainShapesAreSatisfied)
                return matching;
        }
        return null;
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

    // FIXME make sure this is called only when needed, that is, when there are extends => need to validate extends separately
    /** A map that with every shape expr label from correspondingSorbe associates the triples that
     * in satisfyingMatching are mapped to a triple constraint from the main shape of the label.
     *
     * @param correspondingSorbe
     * @param satisfyingMatching
     * @return
     */
    private static Map<Node, Set<Triple>> groupByLabel(Map<Node, SorbeTripleExpr> correspondingSorbe,
                                                       Map<Triple, TripleConstraint> satisfyingMatching) {
        // With every triple constraint associates the set of triples matched to it
        EMap<TripleConstraint, Set<Triple>> inverseMatching = satisfyingMatching.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        EMap::new,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        // With every label associates the set of triples matched to some triple constraint the SORBE associated
        // to this label
        return correspondingSorbe.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getSorbeTripleConstraintsOfSorbeSubExpr(e.getValue().sorbe).stream()
                                .flatMap(tc -> inverseMatching.getOrDefault(tc, Set.of()).stream())
                                .collect(Collectors.toSet())));

    }

}
