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
import org.apache.jena.util.iterator.FilterIterator;

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


    /* package */ static Iterator<Map<Node, Set<Triple>>> correctSplitsIterator(Set<Triple> triples,
                                                                  Shape shape,
                                                                  Map<Node, TripleExpr> baseTripleExprs,
                                                                  ValidationContext vCxt,
                                                                  AShexReport report,
                                                                  ShapeExpr exprForReport, /* TODO replace */
                                                                      Node nodeForReport /* TODO replace*/) {
        Map<Node, SorbeTripleExpr> toBeMatched = new HashMap<>(baseTripleExprs.size());
        for (Map.Entry<Node, TripleExpr> e: baseTripleExprs.entrySet()) {
            toBeMatched.put(e.getKey(), vCxt.getSorbe(e.getValue()));
        }

        Map<Triple, List<TripleConstraint>> preMatching = preMatching_rec_sorbe(triples, toBeMatched.values(),
                shape.getExtras(), vCxt, report, exprForReport, nodeForReport);

        Iterator<Map<Triple, TripleConstraint>> correctMatchingsIterator = new FilterIterator<Map<Triple, TripleConstraint>>(
                m -> matchingSatisfiesTripleExpression_sorbe(m, toBeMatched.values(), vCxt),
                new MatchingsIterator(preMatching));

        return new Iterator<Map<Node, Set<Triple>>>() {
            @Override
            public boolean hasNext() {
                return correctMatchingsIterator.hasNext();
            }

            @Override
            public Map<Node, Set<Triple>> next() {
                return groupByLabel(toBeMatched, correctMatchingsIterator.next());
            }
        };
    }

    /** A pre-matching that with every triple associates the triple constraints it satisfies, after recursive validation. */
    private static Map<Triple, List<TripleConstraint>> preMatching_rec_sorbe(Set<Triple> triples,
                                                                             Collection<SorbeTripleExpr> toBeMatched,
                                                                             Set<Node> extraPredicates,
                                                                             ValidationContext vCxt,
                                                                             AShexReport report,
                                                                             ShapeExpr exprForReport,
                                                                             Node nodeForReport) {
        // 1. With every triple, associate all the triple constraints that this triple could match
        Map<Triple, List<TripleConstraint>> preMatching = predicateBasedPreMatching(triples, toBeMatched);

        // 2. Recursively validate every pair (triple, tripleConstraint), while removing those that are not valid
        filterRecursiveValidation(preMatching, vCxt, report);

        // 3. Check that all unmatched triples are allowed by extra and remove them from the pre-matching
        Set<Triple> unmatchedNonExtra = filterExtra(preMatching, extraPredicates);
        if (null != unmatchedNonExtra) {
            report.addInfoFailure(exprForReport, nodeForReport, triples,
                    "The triples match none of the triples constraints and are not allowed by extra" + unmatchedNonExtra);
        }
        return preMatching;
    }

    /**
     * Removes from the pre matching the extra triples (i.e. captured by an extra predicate)
     * Returns the unmatched triples that are not allowed by extra, or null if no such exist.
     *
     * @param preMatching
     * @param extraPredicates
     * @return
     */
    private static Set<Triple> filterExtra(Map<Triple, List<TripleConstraint>> preMatching,
                                           Set<Node> extraPredicates) {
        Set<Triple> unmatched = unmatchedTriples(preMatching);
        Set<Triple> unmatchedNonExtra = forbiddenByExtra(unmatched, extraPredicates);

        if (! unmatchedNonExtra.isEmpty())
            return unmatchedNonExtra;

        // Remove the extra unmatched triples from the pre-matching
        for (Triple t: unmatched)
            preMatching.remove(t);
        return null;
    }


    /** A map that with every shape expr label from correspondingSorbe associates the triples that
     * in satisfyingMatching are mapped to a triple constraint from the main shape of the label.
     *
     * @param correspondingSorbe contains null as key
     * @param satisfyingMatching
     * @return
     */
    private static Map<Node, Set<Triple>> groupByLabel(Map<Node, SorbeTripleExpr> correspondingSorbe,
                                                       Map<Triple, TripleConstraint> satisfyingMatching) {
        // TODO: correspondingSorbe contains the null key for the base shape. The same holds for the returned map
        // With every triple constraint associates the set of triples matched to it
        EMap<TripleConstraint, Set<Triple>> inverseMatching = satisfyingMatching.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        EMap::new,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        // With every label associates the set of triples matched to some triple constraint the SORBE associated
        // to this label
        Map<Node, Set<Triple>> result = new HashMap<>();
        for (Map.Entry<Node, SorbeTripleExpr> e: correspondingSorbe.entrySet()) {
            result.put(e.getKey(),
                    e.getValue().getSorbeTripleConstraintsOfSorbeSubExpr(e.getValue().sorbe).stream()
                            .flatMap(tc -> inverseMatching.getOrDefault(tc, Set.of()).stream())
                            .collect(Collectors.toSet()));
        }
        return result;
    }



    private static Map<Triple, List<TripleConstraint>> predicateBasedPreMatching (Set<Triple> triples,
                                                                                 Collection<SorbeTripleExpr> toBeMatched) {
        Map<Triple, List<TripleConstraint>> preMatching = triples.stream()
                .collect(Collectors.toMap(Function.identity(),
                        t -> new ArrayList<>()));
        for (SorbeTripleExpr sorbeTripleExpr : toBeMatched) {
            // this loop is needed only for extends, but does no harm w/o extends // TODO note: here, we need the actual shape to be in the map
            Map<Triple, List<TripleConstraint>> pm = sorbeTripleExpr.getPredicateBasedPreMatching(triples);
            pm.forEach((triple, list) -> preMatching.get(triple).addAll(list));
        }
        return preMatching;
    }

    private static void filterRecursiveValidation (Map<Triple, List<TripleConstraint>> preMatching,
                                                  ValidationContext vCxt,
                                                  AShexReport report) {
        preMatching.forEach((triple, matchingTripleConstraints) -> {
            Iterator<TripleConstraint> it = matchingTripleConstraints.iterator();
            while (it.hasNext()) {
                TripleConstraint tc = it.next();
                ShapeExpr valueExpr = tc.getValueExpr();
                Node opposite = tc.isInverse() ? triple.getSubject() : triple.getObject();
                if (!ShapeExprEval.satisfies(valueExpr, opposite, vCxt, report))
                    it.remove();
            }});
    }

    private static Set<Triple> unmatchedTriples(Map<Triple, List<TripleConstraint>> preMatching) {

        return preMatching.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static Set<Triple> forbiddenByExtra(Set<Triple> unmatchedTriples, Set<Node> extraPredicates) {
        return unmatchedTriples.stream()
                .filter(t ->  !extraPredicates.contains(t.getPredicate()))
                .collect(Collectors.toSet());
    }

    private static boolean matchingSatisfiesTripleExpression_sorbe(Map<Triple, TripleConstraint> matching,
                                                                  Collection<SorbeTripleExpr> toBeMatched,
                                                                  ValidationContext vCxt) {

        return toBeMatched.stream().allMatch(sorbeTripleExpr -> {
            // this loop is needed only for extends, but does no harm w/o extends
            Cardinality interval = sorbeTripleExpr.computeInterval(matching);
            return interval.min <= 1 && 1 <= interval.max
                    // the triple expression is satisfied by the matching, check semantic actions
                    &&
                    sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                            .allMatch(p -> vCxt.dispatchTripleExprSemanticAction(p.getKey(), p.getValue()));
        });
    }



}
