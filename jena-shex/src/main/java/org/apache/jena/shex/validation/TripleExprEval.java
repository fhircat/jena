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

// TODO remove the use of the word sorbe whenever non relevant

/** Utilities for matching triple expressions.
 *
 * Vocabulary:
 * - a pre-matching is a Map<Triple, List<TripleConstraint>>. Typically, preMatching.get(t) are triple constraints that could possibly be matched with triple, for instance having the same predicate.
 * - a matching is a Map<Triple, TripleConstraint> that with every triple associates a unique triple constraint to which it is matched. For instance, a pre-matching can be seen as a set of matchings obtained by choosing a single triple constraint for every triple.
 * - a split is a Map<Node, Set<Triple>> that with a node representing a shape expression label associates a set of triples. It is used to indicate which triples are matched with which supertypes of a given shape.
 */
public class TripleExprEval {

    /* package */ static Iterator<Map<Node, Set<Triple>>> correctSplitsIterator(
            Node dataNode, Shape shape, Set<Triple> triples,
            Map<Node, TripleExpr> mainTripleExprs, ValidationContext vCxt, Reporter shapeReporter) {

        return correctSplitsIterator_sorbe(dataNode, shape, triples, mainTripleExprs, vCxt, shapeReporter);
    }

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
    private static Iterator<Map<Node, Set<Triple>>> correctSplitsIterator_sorbe(
            Node dataNode, Shape shape, Set<Triple> triples,
            Map<Node, TripleExpr> mainTripleExprs, ValidationContext vCxt, Reporter shapeReporter) {

        // Does not notify the reporter about conformant / non-conformant

        // NOTE: SORBE used
        // Constructs the thriple expressions to be validated
        Map<Node, TripleExprForValidation> exprsToBeMatched = new HashMap<>(mainTripleExprs.size());
        for (Map.Entry<Node, TripleExpr> e: mainTripleExprs.entrySet()) {

            // if (! vCxt.getSorbe(e.getValue()).isNativeSorbe()) throw new IllegalStateException("NOT SORBE"); // TODO for debugging, remove eventually together with isNativeSorbe
            exprsToBeMatched.put(e.getKey(), vCxt.getExprForValidation(e.getValue()));
        }
        shapeReporter.informShapeTripleExpressions(exprsToBeMatched);

        // With every triple, associate all the triple constraints that this triple could match, based on predicate
        Map<Triple, List<TripleConstraint>> predicateBasedPreMatching
                = predicateBasedPreMatching(triples, exprsToBeMatched.values());
        shapeReporter.informPredicateBasedPreMatching(Collections.unmodifiableMap(predicateBasedPreMatching));

        // Recursively validate every pair (triple, tripleConstraint), while removing those that are not valid
        Map<Triple, List<TripleConstraint>> preMatching = filterRecursiveValidation(predicateBasedPreMatching, vCxt, shapeReporter);

        // Check that all unmatched triples are allowed by extra and remove them from the pre-matching
        Set<Triple> unmatchedTriples = new HashSet<>();
        Map<Triple, List<TripleConstraint>> cleanPreMatching = filterUnmatchedTriples(preMatching, unmatchedTriples, shapeReporter);
        shapeReporter.informPreMatching(Collections.unmodifiableMap(cleanPreMatching));

        if (! unmatchedTriplesAreExtra(unmatchedTriples, shape.getExtras())) {
            shapeReporter.informUnexpectedTriples(unmatchedTriples, Reporter.UnexpectedTriplesReason.EXTRA);
            return Collections.emptyIterator();
        }

        // NOTE: SORBE used
        Iterator<Map<Triple, TripleConstraint>> correctMatchingsIterator = new FilterIterator<>(
                m -> checkSatisfiesTripleExpressionsAndReport_sorbe(dataNode,
                        m, exprsToBeMatched.values(), vCxt, shapeReporter),
                new MatchingsIterator(cleanPreMatching));

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
     * by recursively validating t's object against the triple constraint's object constraint.
     * Returns the filtered pre matching (which is possibly a modified version of the one given in parameter) */
    private static Map<Triple, List<TripleConstraint>> filterRecursiveValidation (
            Map<Triple, List<TripleConstraint>> preMatching,
            ValidationContext vCxt,
            Reporter shapeReporter) {
        //if (! shapeReporter.isValidateOnly()) {
        //    preMatching = new HashMap<>(preMatching);
        //}
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
        return preMatching;
    }

    /* The triples that are unmatched in preMatching, i.e. which associated value is an empty list.*/
    private static Map<Triple, List<TripleConstraint>> filterUnmatchedTriples(
            Map<Triple, List<TripleConstraint>> preMatching,
            Set<Triple> removedTriples,
            Reporter shapeReporter) {

        //if (! shapeReporter.isValidateOnly())
        //    preMatching = new HashMap<>(preMatching);

        preMatching.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .forEach(removedTriples::add);
        removedTriples.forEach(preMatching::remove);
        return preMatching;
    }

    /** Checks whether a matching satisfies a hierarchy of triple expressions. */
    private static boolean checkSatisfiesTripleExpressionsAndReport_sorbe(Node dataNode,
                                                                          Map<Triple, TripleConstraint> matching,
                                                                          Collection<TripleExprForValidation> exprsToBeMatched,
                                                                          ValidationContext vCxt,
                                                                          Reporter shapeReporter) {
        // Does not notify the reporter about conformant / non-conformant

        boolean teValid = true;
        boolean seValid = true;
        exprsToBeMatchedLoop:
        for (TripleExprForValidation teVal : exprsToBeMatched) {
            // this loop is needed only for extends, but does no harm w/o extends

            // here, teValid is always true (because of break when ! teValid)
            teValid = teVal.isValid(matching);

            // TODO is the tripleExprReporter needed ?
            //Reporter tripleExprReporter = shapeReporter.createChild(dataNode, teVal.getOriginTripleExpr(),
            //        getMatchedTriplesForReporter(matching, vCxt, shapeReporter, teVal));

            //shapeReporter.informCandidateMatchingConformance(teValid, matching, teVal.getOriginTripleExpr());

            if (!teValid) break;

            for (Pair<TripleExpr, Set<Triple>> p : teVal.getSemActsSubExprsAndTheirMatchedTriples(matching, vCxt)) {
                if (! vCxt.dispatchTripleExprSemanticAction(
                        p.getKey(), p.getValue(), /*tripleExprReporter*/ shapeReporter, dataNode)) {
                    seValid = false;
                    break exprsToBeMatchedLoop;
                }
            }
        }
        if (teValid && seValid) {
            shapeReporter.informCandidateMatchingConformance(true, matching, (Reporter.MatchingNotSatisfiedReason) null);
            return true;
        }
        shapeReporter.informCandidateMatchingConformance(false, matching,
                !teValid ? Reporter.MatchingNotSatisfiedReason.TRIPLE_EXPRS : Reporter.MatchingNotSatisfiedReason.SEM_ACTS);
        return false;
    }

    // TODO: useful ?
    private static Set<Triple> getMatchedTriplesForReporter(
            Map<Triple, TripleConstraint> matching,
            ValidationContext vCxt,
            Reporter shapeReporter,
            TripleExprForValidation teVal) {

        if (shapeReporter.isValidateOnly())
            return null;

        return teVal.triplesMatchedInOriginSubExpr(matching, teVal.getOriginTripleExpr(), vCxt);
    }


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
