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

import org.apache.commons.lang3.tuple.Pair;
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
    // TODO replace streams with loops for efficiency

    /* package */ static Iterator<Map<Node, Set<Triple>>> correctSplitsIterator(
            Node dataNode, Shape shape, Set<Triple> triples,
            Map<Node, TripleExpr> mainTripleExprs, ValidationContext vCxt, Reporter shapeReporter) {

        // Does not notify the reporter about conformant / non-conformant

        // Constructs the triple expressions to be validated
        Map<Node, TripleExprForValidation> exprsToBeMatched = new HashMap<>(mainTripleExprs.size());
        for (Map.Entry<Node, TripleExpr> e: mainTripleExprs.entrySet()) {
            exprsToBeMatched.put(e.getKey(), vCxt.getExprForValidation(e.getValue()));
        }
        shapeReporter.informShapeTripleExpressions(exprsToBeMatched);

        // With every triple, associate all the triple constraints having the same predicate
        Map<Triple, List<TripleConstraint>> preMatching
                = predicateBasedPreMatching(triples, exprsToBeMatched.values());
        shapeReporter.informPredicateBasedPreMatching(Collections.unmodifiableMap(preMatching), null);

        // Recursively validate every pair (triple, tripleConstraint), while removing those that are not valid
        filterRecursiveValidation(preMatching, vCxt, shapeReporter);

        // Check that all unmatched triples are allowed by extra and remove them from the pre-matching
        Set<Triple> unmatchedTriples = new HashSet<>();
        filterUnmatchedTriples(preMatching, unmatchedTriples);

        if (! unmatchedTriplesAreExtra(unmatchedTriples, shape.getExtras())) {
            shapeReporter.informUnexpectedTriples(unmatchedTriples, Reporter.UnexpectedTriplesReason.EXTRA);
            return Collections.emptyIterator();
        }

        // Prepare the iterator over matchings to be returned.
        // Enumerates only the matchings not refuted by the feasibility analysis; every valid
        // matching is enumerated (see FeasibleMatchingsIterator).
        Iterator<Map<Triple, TripleConstraint>> allMatchingsIterator =
                new FeasibleMatchingsIterator(preMatching, exprsToBeMatched.values());

        /// <errorReporting>
        /// Keep only the matchings that associate at least one triple with every mandatory triple constraint
        ///      as they are more suitable for error reporting
        Iterator<Map<Triple, TripleConstraint>> mandatoryTCSatisfiedMatchingsIterator = new FilterIterator<> (
                m -> checkAllMandatoryTripleConstraintsAreMatched(m, exprsToBeMatched),
                allMatchingsIterator);

        if (! shapeReporter.isValidateOnly()) {
            // Probe over the unpruned matchings, so that reporting is independent of the search pruning
            Iterator<Map<Triple, TripleConstraint>> mandatoryProbe = new FilterIterator<> (
                    m -> checkAllMandatoryTripleConstraintsAreMatched(m, exprsToBeMatched),
                    new MatchingsIterator(preMatching));
            if (! mandatoryProbe.hasNext()) {
                // Take one matching on which to report
                Iterator<Map<Triple, TripleConstraint>> it = new MatchingsIterator(preMatching);
                if (it.hasNext()) {
                    Map<Triple, TripleConstraint> matching = it.next();
                    shapeReporter.informUnmatchedTripleConstraints(unmatchedMandatoryTripleConstraints(matching, exprsToBeMatched));
                }
            }
        }
        /// </errorReporting>

        // Iterator over the matchings that satisfy all the triple expressions
        Iterator<Map<Triple, TripleConstraint>> correctMatchingsIterator = new FilterIterator<>(
                m ->
                        checkSatisfiesTripleExpressionsAndReport(dataNode, m, exprsToBeMatched.values(), vCxt, shapeReporter),
                mandatoryTCSatisfiedMatchingsIterator);

        // Iterator over the elements of split of the set of triples among the triple expressions to be matched
        Iterator<Map<Node, Set<Triple>>> result = new Iterator<>(){
            @Override
            public boolean hasNext() {
                return correctMatchingsIterator.hasNext();
            }

            @Override
            public Map<Node, Set<Triple>> next() {
                return groupByLabel(exprsToBeMatched, correctMatchingsIterator.next());
            }
        };

        ///  Error reporting : in case the expressions are not satisfiable, communicate the pre-matching to the
        ///        error reporter
        if (! result.hasNext())
            shapeReporter.informPreMatching(Collections.unmodifiableMap(preMatching), false);

        return result;
    }

    /** Matches every triple to the list of expressions that have the same predicate. */
    private static Map<Triple, List<TripleConstraint>> predicateBasedPreMatching (
            Set<Triple> triples,
            Collection<TripleExprForValidation> toBeMatched) {

        Map<Triple, List<TripleConstraint>> preMatching = triples.stream()
                .collect(Collectors.toMap(Function.identity(),
                        t -> new ArrayList<>()));
        for (TripleExprForValidation teVal : toBeMatched) {
            // this loop is needed only for extends, but does no harm w/o extends
            Map<Triple, List<TripleConstraint>> pm = teVal.getPredicateBasedPreMatching(triples);
            pm.forEach((triple, list) -> preMatching.get(triple).addAll(list));
        }
        return preMatching;
    }

    /** Filters a pre-matching by keeping in preMatching.get(t) only those triple constraints that are satisfied by t,
     * by recursively validating t's object against the triple constraint's object constraint. */
    private static void filterRecursiveValidation (
            Map<Triple, List<TripleConstraint>> preMatching,
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

    /* Filters a pre-matching by keeping in only those triples that have at least one associated triple constraint.
    * Collects the removed triples. */
    private static void filterUnmatchedTriples(
            Map<Triple, List<TripleConstraint>> preMatching,
            Set<Triple> removedTriples) {

        preMatching.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .forEach(removedTriples::add);
        removedTriples.forEach(preMatching::remove);
    }


    /** The mandatory triple constraints are those that would have at least one associated triple in every correct matching. */
    private static boolean checkAllMandatoryTripleConstraintsAreMatched(Map<Triple, TripleConstraint> matching,
                                                                        Map<Node, TripleExprForValidation> exprsToBeMatched) {
        for (TripleExprForValidation teVal : exprsToBeMatched.values()) {
            ESet<TripleConstraint> matchedTCs = new ESet<>();
            matchedTCs.addAll(matching.values());
            if (! matchedTCs.containsAll(teVal.mandatoryTripleConstraints())) {
                return false;
            }
        }
        return true;
    }

    /** Compute the mandatory triple constraints that were not matched by the pre-matching. */
    private static List<TripleConstraint> unmatchedMandatoryTripleConstraints(Map<Triple, TripleConstraint> matching,
                                                                      Map<Node, TripleExprForValidation> exprsToBeMatched) {
        ArrayList<TripleConstraint> result = new ArrayList<>();
        for (TripleExprForValidation teVal : exprsToBeMatched.values()) {
            ESet<TripleConstraint> matchedTCs = new ESet<>();
            matchedTCs.addAll(matching.values());
            for (TripleConstraint tc : teVal.mandatoryTripleConstraints()) {
                if (! matchedTCs.contains(tc)) {
                    result.add(tc);
                }
            }
        }
        return result;

    }

    /** Checks whether a matching satisfies a collection of triple expressions, corresponding to a hierarchy of shapes. */
    private static boolean checkSatisfiesTripleExpressionsAndReport(Node dataNode,
                                                                    Map<Triple, TripleConstraint> matching,
                                                                    Collection<TripleExprForValidation> exprsToBeMatched,
                                                                    ValidationContext vCxt,
                                                                    Reporter shapeReporter) {
        // Does not notify the reporter about conformant / non-conformant

        boolean teValid = true;     // triple expressions are valid
        boolean saValid = true;     // semantic actions are valid

        exprsToBeMatchedLoop:
        for (TripleExprForValidation teVal : exprsToBeMatched) {

            // here, teValid is always true (because of break when ! teValid)
            teValid = teVal.isValid(matching);
            if (!teValid) break;

            for (Pair<TripleExpr, Set<Triple>> p : teVal.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt)) {
                if (! vCxt.dispatchTripleExprSemanticAction(
                        p.getKey(), p.getValue(), /*tripleExprReporter*/ shapeReporter, dataNode)) {
                    saValid = false;
                    break exprsToBeMatchedLoop;
                }
            }
        }
        if (teValid && saValid) {
            shapeReporter.informCandidateMatchingConformance(true, matching, null);
            return true;
        }
        shapeReporter.informCandidateMatchingConformance(false, matching,
                !teValid ? Reporter.MatchingNotSatisfiedReason.TRIPLE_EXPRS : Reporter.MatchingNotSatisfiedReason.SEM_ACTS);
        return false;
    }

    /** Checks if the unmatched triples all have a predicate among the extra predicates. */
    private static boolean unmatchedTriplesAreExtra(Set<Triple> unmatchedTriples, Set<Node> extraPredicates) {
        return unmatchedTriples.stream().allMatch(t -> extraPredicates.contains(t.getPredicate()));
    }

    /** With every shape expression label (in an extension hierarchy), associates the triples that {@param matching}
     * matched to a triple constraint from the definition of that label.
     * More precisely, with every label l in {@param expressions}.keySet(), associates the set of triples t from
     * {@param matching}.keySet() s.t. {@param matching.get(t)} is a sub-expression of {@param expressions}.get(l).
     *
     * @param expressions Can contain null as key.
     * @param matching
     * @return Has the same key set as {@param expressions}, thus can contain null key.
     */
    private static Map<Node, Set<Triple>> groupByLabel(Map<Node, TripleExprForValidation> expressions,
                                                       Map<Triple, TripleConstraint> matching) {
        // With every triple constraint associates the set of triples matched to it
        EMap<TripleConstraint, Set<Triple>> inverseMatching = matching.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        EMap::new,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        // Associate the triples with the labels by tracking the label in which definition the triple constraint appears
        Map<Node, Set<Triple>> result = new HashMap<>();
        for (Map.Entry<Node, TripleExprForValidation> e: expressions.entrySet()) {
            result.put(e.getKey(),
                    e.getValue().getTripleConstraints().stream()
                            .flatMap(tc -> inverseMatching.getOrDefault(tc, Set.of()).stream())
                            .collect(Collectors.toSet()));
        }
        return result;
    }
}
