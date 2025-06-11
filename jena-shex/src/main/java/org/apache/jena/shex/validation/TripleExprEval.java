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
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.reporting.Reporter;
import org.apache.jena.util.iterator.FilterIterator;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Utilities for matching triple expressions.
 *
 * Vocabulary:
 * - a pre-matching is a Map<Triple, List<TripleConstraint>>. Typically, preMatching.get(t) are triple constraints that could possibly be matched with triple, for instance having the same predicate.
 * - a matching is a Map<Triple, TripleConstraint> that with every triple associates a unique triple constraint to which it is matched. For instance, a pre-matching can be seen as a set of matchings obtained by choosing a single triple constraint for every triple.
 * - a split is a Map<Node, Set<Triple>> that with a node representing a shape expression label associates a set of triples. It is used to indicate which triples are matched with which supertypes of a given shape.
 */
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

    /** Iterator over all valid splittings of a set of triples between the triple expressions along the supertypes of a shape.
     * A split is valid if the set of triples split.get(supertypeLabel) satisfies the triple expression baseTripleExprs.get(supertypeLabel)
     *
     * @param triples The set of triples to be split
     * @param shape The shape that is being validated
     * @param baseTripleExprs The triple expressions of the shape's supertypes, indexed by the supertype's labels
     * @param vCxt
     * @param reporter
     * @param exprForReport
     * @param nodeForReport
     * @return An element of this iterator associates a subset of triples with every supertype label key of baseTripleExprs
     */
    static Iterator<Map<Node, Set<Triple>>> correctSplitsIterator(Set<Triple> triples,
                                                                  Shape shape,
                                                                  Map<Node, TripleExpr> baseTripleExprs,
                                                                  ValidationContext2 vCxt,
                                                                  Reporter reporter,
                                                                  ShapeExpr exprForReport, /* TODO replace */
                                                                  Node nodeForReport /* TODO replace*/) {
        Map<Node, SorbeTripleExpr> toBeMatched = new HashMap<>(baseTripleExprs.size());
        for (Map.Entry<Node, TripleExpr> e: baseTripleExprs.entrySet()) {
            toBeMatched.put(e.getKey(), vCxt.getSorbe(e.getValue()));
        }

        Map<Triple, List<TripleConstraint>> preMatching = preMatching_rec_sorbe(triples, toBeMatched.values(),
                shape.getExtras(), vCxt, reporter, exprForReport, nodeForReport);

        Iterator<Map<Triple, TripleConstraint>> correctMatchingsIterator = new FilterIterator<Map<Triple, TripleConstraint>>(
                m -> matchingSatisfiesTripleExpression_sorbe(m, toBeMatched.values(), vCxt, reporter, nodeForReport),
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
                                                                             ValidationContext2 vCxt,
                                                                             Reporter reporter,
                                                                             ShapeExpr exprForReport,
                                                                             Node nodeForReport) {
        // 1. With every triple, associate all the triple constraints that this triple could match
        Map<Triple, List<TripleConstraint>> preMatching = predicateBasedPreMatching(triples, toBeMatched);

        // 2. Recursively validate every pair (triple, tripleConstraint), while removing those that are not valid
        filterRecursiveValidation(preMatching, vCxt, reporter);

        // 3. Check that all unmatched triples are allowed by extra and remove them from the pre-matching
        Set<Triple> unmatchedNonExtra = filterExtra(preMatching, extraPredicates);
        if (null != unmatchedNonExtra) {
            reporter.setIsConformant(false,
                    "The triples match none of the triples constraints and are not allowed by extra",
                    unmatchedNonExtra);
        }
        return preMatching;
    }

    /** Removes the extra triples from the pre-matching.
     * Returns the unmatched triples that are not allowed by extra, or null if no such exist.
     * Un unmatched triple is a triple with preMatching(triple).isEmpty(). An unmatched triple is an extra triple if its predicate is in extraPredicates. Otherwise, it indicates an error.
     *
     * @param preMatching A pre-matching to be filtered.
     * @param extraPredicates Define the triples that are allowed by extra.
     * @return null if all unmatched triples are extra, or the set of non-extra unmatched triples otherwise (ie those that correspond to errors)
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


    /** With every shape expression label l from expressions.keySet(), associates the triples t from matching.keySet() s.t. matching.get(t) is a sub-expression of expressions.get(l).
     *
     * @param expressions Can contain null as key.
     * @param matching
     * @return
     */
    private static Map<Node, Set<Triple>> groupByLabel(Map<Node, SorbeTripleExpr> expressions,
                                                       Map<Triple, TripleConstraint> matching) {
        // TODO: expressions contains the null key for the base shape. The same holds for the returned map
        // With every triple constraint associates the set of triples matched to it
        EMap<TripleConstraint, Set<Triple>> inverseMatching = matching.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        EMap::new,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        // With every label associates the set of triples matched to some triple constraint the SORBE associated
        // to this label
        Map<Node, Set<Triple>> result = new HashMap<>();
        for (Map.Entry<Node, SorbeTripleExpr> e: expressions.entrySet()) {
            result.put(e.getKey(),
                    e.getValue().getSorbeTripleConstraintsOfSorbeSubExpr(e.getValue().sorbe).stream()
                            .flatMap(tc -> inverseMatching.getOrDefault(tc, Set.of()).stream())
                            .collect(Collectors.toSet()));
        }
        return result;
    }



    /** Matches every triple to the list of expressions that have the same predicate. */
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

    /** Filters a pre-matching by keeping in preMatching.get(t) only those triple constraints that are satisfied by t, by recursively validating t's object against the triple constraint's object constraint.*/
    private static void filterRecursiveValidation (Map<Triple, List<TripleConstraint>> preMatching,
                                                  ValidationContext2 vCxt,
                                                  Reporter reporter) {
        preMatching.forEach((triple, matchingTripleConstraints) -> {
            Iterator<TripleConstraint> it = matchingTripleConstraints.iterator();
            while (it.hasNext()) {
                TripleConstraint tc = it.next();
                ShapeExpr valueExpr = tc.getValueExpr();
                Node opposite = tc.isInverse() ? triple.getSubject() : triple.getObject();
                if (!ShapeExprEval.satisfies(opposite, valueExpr, vCxt, reporter))
                    it.remove();
            }});
    }

    /* The triples that are unmatched in preMatching, i.e. which associated value is an empty list.*/
    private static Set<Triple> unmatchedTriples(Map<Triple, List<TripleConstraint>> preMatching) {

        return preMatching.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /** The triples among unmatchedTriples which predicate is not in extraPredicates. */
    private static Set<Triple> forbiddenByExtra(Set<Triple> unmatchedTriples, Set<Node> extraPredicates) {
        return unmatchedTriples.stream()
                .filter(t ->  !extraPredicates.contains(t.getPredicate()))
                .collect(Collectors.toSet());
    }

    /** Checs whether a matching satisfies a hierarchy of triple expressions. */
    private static boolean matchingSatisfiesTripleExpression_sorbe(Map<Triple, TripleConstraint> matching,
                                                                   Collection<SorbeTripleExpr> toBeMatched,
                                                                   ValidationContext2 vCxt,
                                                                   Reporter reporter,
                                                                   Node nodeForReport) {

        return toBeMatched.stream().allMatch(sorbeTripleExpr -> {
            // this loop is needed only for extends, but does no harm w/o extends
            Cardinality interval = sorbeTripleExpr.computeInterval(matching);
            return interval.min <= 1 && 1 <= interval.max
                    // the triple expression is satisfied by the matching, check semantic actions
                    &&
                    sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                            .allMatch(p -> vCxt.dispatchTripleExprSemanticAction(p.getKey(), p.getValue(), reporter, nodeForReport));
        });
    }

}
