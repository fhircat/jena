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

    /**
     * Iterator over all valid splittings of a set of triples between the triple expressions along the supertypes of a shape.
     * A split is valid if the set of triples split.get(supertypeLabel) satisfies the triple expression baseTripleExprs.get(supertypeLabel)
     *
     * @param dataNode
     * @param shape           The shape that is being validated
     * @param triples         The set of triples to be split
     * @param mainTripleExprs The triple expressions of the shape's supertypes, indexed by the supertype's labels
     * @param vCxt
     * @param shapeReporter
     * @return An element of this iterator associates a subset of triples with every supertype label key of baseTripleExprs
     */
    /* package */ static Iterator<Map<Node, Set<Triple>>> correctSplitsIterator(Node dataNode,
                                                                                Shape shape,
                                                                                Set<Triple> triples,
                                                                                Map<Node, TripleExpr> mainTripleExprs,
                                                                                ValidationContext vCxt,
                                                                                Reporter shapeReporter) {
        // Does not notify the reporter about conformant / non-conformant

        Map<Node, SorbeTripleExpr> exprsToBeMatched = new HashMap<>(mainTripleExprs.size());
        for (Map.Entry<Node, TripleExpr> e: mainTripleExprs.entrySet()) {
            exprsToBeMatched.put(e.getKey(), vCxt.getSorbe(e.getValue()));
        }

        Map<Triple, List<TripleConstraint>> preMatching = preMatching_rec_sorbe(dataNode, shape, triples, exprsToBeMatched.values(),
                shape.getExtras(), vCxt, shapeReporter);

        if (preMatching == null)
            return Collections.emptyIterator();

        Iterator<Map<Triple, TripleConstraint>> correctMatchingsIterator = new FilterIterator<>(
                m -> matchingSatisfiesTripleExpression_sorbe(dataNode,
                        m, exprsToBeMatched.values(), vCxt, shapeReporter),
                new MatchingsIterator(preMatching));

        return new Iterator<Map<Node, Set<Triple>>>() {
            @Override
            public boolean hasNext() {
                return correctMatchingsIterator.hasNext();
            }

            @Override
            public Map<Node, Set<Triple>> next() {
                return groupByLabel(exprsToBeMatched, correctMatchingsIterator.next());
            }
        };
    }

    /** A pre-matching that with every triple associates the triple constraints it satisfies, after recursive validation.
     * Returns null if there were triples not allowed by extra. */
    private static Map<Triple, List<TripleConstraint>> preMatching_rec_sorbe(Node dataNode,
                                                                             ShapeExpr shape,
                                                                             Set<Triple> triples,
                                                                             Collection<SorbeTripleExpr> exprsToBeMatched,
                                                                             Set<Node> extraPredicates,
                                                                             ValidationContext vCxt,
                                                                             Reporter shapeReporter) {
        // 1. With every triple, associate all the triple constraints that this triple could match
        Map<Triple, List<TripleConstraint>> preMatching = predicateBasedPreMatching(triples, exprsToBeMatched);

        // 2. Recursively validate every pair (triple, tripleConstraint), while removing those that are not valid
        filterRecursiveValidation(preMatching, vCxt, shapeReporter);

        // 3. Check that all unmatched triples are allowed by extra and remove them from the pre-matching
        Set<Triple> unmatchedNonExtra = filterExtra(preMatching, extraPredicates);
        if (null != unmatchedNonExtra) {
            shapeReporter.addForbiddenExtraInfo(
                    "The triples match none of the triple constraints and are not allowed by extra",
                    unmatchedNonExtra);
            return null;
        }
        return preMatching;
    }

    /** Removes the extra triples from the pre-matching.
     * Returns the unmatched triples that are not allowed by extra, or null if no such exist.
     * An unmatched triple is a triple t s.t. preMatching(t).isEmpty(). An unmatched triple is an extra triple if its predicate is in extraPredicates. Otherwise, it indicates an error.
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
            // this loop is needed only for extends, but does no harm w/o extends
            Map<Triple, List<TripleConstraint>> pm = sorbeTripleExpr.getPredicateBasedPreMatching(triples);
            pm.forEach((triple, list) -> preMatching.get(triple).addAll(list));
        }
        return preMatching;
    }

    /** Filters a pre-matching by keeping in preMatching.get(t) only those triple constraints that are satisfied by t, by recursively validating t's object against the triple constraint's object constraint.*/
    private static void filterRecursiveValidation (Map<Triple, List<TripleConstraint>> preMatching,
                                                  ValidationContext vCxt,
                                                  Reporter shapeReporter) {
        preMatching.forEach((triple, matchingTripleConstraints) -> {
            Iterator<TripleConstraint> it = matchingTripleConstraints.iterator();
            while (it.hasNext()) {
                TripleConstraint tc = it.next();
                ShapeExpr valueExpr = tc.getValueExpr();
                Node opposite = tc.isInverse() ? triple.getSubject() : triple.getObject();
                Reporter subReporter = shapeReporter.createChild(opposite, valueExpr, null);
                if (!ShapeExprEval.satisfies(opposite, valueExpr, vCxt, subReporter))
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

    /** Checks whether a matching satisfies a hierarchy of triple expressions. */
    private static boolean matchingSatisfiesTripleExpression_sorbe(Node dataNode,
                                                                   Map<Triple, TripleConstraint> matching,
                                                                   Collection<SorbeTripleExpr> exprsToBeMatched,
                                                                   ValidationContext vCxt,
                                                                   Reporter shapeReporter) {
        // Does not notify the reporter about conformant / non-conformant

        return exprsToBeMatched.stream().allMatch(sorbeTripleExpr -> {
            // this loop is needed only for extends, but does no harm w/o extends
            TripleExpr originTripleExpr = sorbeTripleExpr.getOriginTripleExpr();
            Reporter tripleExprReporter = shapeReporter.createChild(dataNode, originTripleExpr,
                    sorbeTripleExpr.triplesMatchedInOriginSubExpr(matching, originTripleExpr, vCxt));

            Cardinality interval = sorbeTripleExpr.computeInterval(matching);
            boolean teValid = interval.min <= 1 && 1 <= interval.max;
            boolean teAndSemActValid = teValid &&
                    sorbeTripleExpr.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt).stream()
                            .allMatch(p -> vCxt.dispatchTripleExprSemanticAction(
                                    p.getKey(), p.getValue(), tripleExprReporter, dataNode));
            return tripleExprReporter.setIsConformant(teAndSemActValid);
        });
    }

}
