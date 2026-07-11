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
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.Shex;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.TripleConstraint;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Differential correctness tests for {@link FeasibleMatchingsIterator} against the plain
 * Cartesian-product {@link MatchingsIterator}, on exhaustive small instances of the triple
 * expression patterns that exercise every rule of {@link TripleExprFeasibility}.
 *
 * The contract under test:
 * <ol>
 * <li><b>Soundness of pruning</b>: every matching emitted by the feasible iterator is also
 *     emitted by the Cartesian product (no invented matchings).</li>
 * <li><b>Completeness</b>: every <em>valid</em> matching of the Cartesian product (as decided by
 *     the exact interval computation, possibly for several expressions jointly as with EXTENDS)
 *     is emitted by the feasible iterator.</li>
 * </ol>
 * Together these guarantee that substituting the feasible iterator preserves the validation
 * relation exactly.
 */
public class TestFeasibleMatchingSearch {

    private static final String PFX = "PREFIX : <http://dt.ex/>\n";

    // Patterns chosen to exercise: OneOf exclusivity (exact mode), OneOf under repetition
    // (mixing allowed), EachOf co-occurrence under repetition (count coupling, deliberately
    // not captured by the feasibility predicate), shared predicates across branches,
    // cardinality upper bounds, optionality, and {0,0}.
    private static final String[] PATTERNS = {
            "{ :p . {1,2} | :p . + ; :q . }",
            "{ :p . * ; (:p . + | :p .) ; :p . }",       // shexTest 'nPlus1' pattern
            "{ (:p . ; :q .)+ }",
            "{ (:p . | :q .)+ }",
            "{ :p . ? ; :p . {2,3} }",
            "{ :p . {0,0} | :p . {2,4} }",
            "{ (:p . ; :q . ?) * ; :q . ? }",
            "{ (:p . {1,2} | :q .) ; (:q . | :p .) ? }",
    };

    @Test
    public void feasibleIteratorAgreesWithCartesianProduct() {
        for (String pattern : PATTERNS) {
            for (int nP = 0; nP <= 4; nP++) {
                for (int nQ = 0; nQ <= 2; nQ++) {
                    checkDifferential(pattern, nP, nQ);
                }
            }
        }
    }

    /** EXTENDS is a conjunction of triple expressions over the same matching: the joint validity
     * of a matching is the conjunction of per-expression interval checks. Exercised here with two
     * expressions sharing predicates, as in an extension hierarchy. */
    @Test
    public void feasibleIteratorAgreesOnConjunctions() {
        String[][] conjunctions = {
                { "{ :p . ; :q . * }", "{ :p . ? ; :q . {1,2} }" },
                { "{ (:p . | :q .) + }", "{ :p . {1,3} ; :q . * }" },
        };
        for (String[] pair : conjunctions) {
            for (int nP = 0; nP <= 3; nP++) {
                for (int nQ = 0; nQ <= 3; nQ++) {
                    checkDifferentialConjunction(pair, nP, nQ);
                }
            }
        }
    }

    private void checkDifferential(String pattern, int nP, int nQ) {
        TripleExprForValidation teVal = exprForValidation("<http://dt.ex/S> " + pattern);
        Set<Triple> triples = triples(nP, nQ);
        Map<Triple, List<TripleConstraint>> preMatching = merge(List.of(teVal), triples);

        String label = pattern + " with nP=" + nP + " nQ=" + nQ;
        compare(label, preMatching, List.of(teVal));
    }

    private void checkDifferentialConjunction(String[] patterns, int nP, int nQ) {
        List<TripleExprForValidation> teVals = new ArrayList<>();
        for (int i = 0; i < patterns.length; i++)
            teVals.add(exprForValidation("<http://dt.ex/S" + i + "> " + patterns[i]));
        Set<Triple> triples = triples(nP, nQ);
        Map<Triple, List<TripleConstraint>> preMatching = merge(teVals, triples);

        String label = String.join(" AND ", patterns) + " with nP=" + nP + " nQ=" + nQ;
        compare(label, preMatching, teVals);
    }

    private void compare(String label,
                         Map<Triple, List<TripleConstraint>> preMatching,
                         List<TripleExprForValidation> teVals) {
        // Skip instances where a triple has no candidate at all: the production code removes
        // such triples (extra/closed handling) before building the iterator.
        if (preMatching.values().stream().anyMatch(List::isEmpty))
            return;

        Set<Map<Triple, Integer>> raw = collect(new MatchingsIterator(preMatching), teVals);
        Set<Map<Triple, Integer>> feasible = collect(new FeasibleMatchingsIterator(preMatching, teVals), teVals);

        assertTrue(label + ": feasible iterator emitted a matching outside the Cartesian product",
                rawSet(preMatching, teVals).containsAll(feasibleSet(preMatching, teVals)));
        assertEquals(label + ": valid matchings differ", raw, feasible);
    }

    /** The canonical forms of the *valid* matchings produced by the iterator. */
    private Set<Map<Triple, Integer>> collect(Iterator<Map<Triple, TripleConstraint>> it,
                                              List<TripleExprForValidation> teVals) {
        Map<TripleConstraint, Integer> tcIndex = tcIndex(teVals);
        Set<Map<Triple, Integer>> result = new HashSet<>();
        while (it.hasNext()) {
            Map<Triple, TripleConstraint> m = it.next();
            boolean valid = teVals.stream().allMatch(teVal -> teVal.isValid(m));
            if (valid)
                result.add(canonical(m, tcIndex));
        }
        return result;
    }

    /** The canonical forms of *all* matchings produced by the iterator. */
    private Set<Map<Triple, Integer>> allOf(Iterator<Map<Triple, TripleConstraint>> it,
                                            List<TripleExprForValidation> teVals) {
        Map<TripleConstraint, Integer> tcIndex = tcIndex(teVals);
        Set<Map<Triple, Integer>> result = new HashSet<>();
        while (it.hasNext())
            result.add(canonical(it.next(), tcIndex));
        return result;
    }

    private Set<Map<Triple, Integer>> rawSet(Map<Triple, List<TripleConstraint>> preMatching,
                                             List<TripleExprForValidation> teVals) {
        return allOf(new MatchingsIterator(preMatching), teVals);
    }

    private Set<Map<Triple, Integer>> feasibleSet(Map<Triple, List<TripleConstraint>> preMatching,
                                                  List<TripleExprForValidation> teVals) {
        return allOf(new FeasibleMatchingsIterator(preMatching, teVals), teVals);
    }

    // ------------------------------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------------------------------

    /** Matchings use SORBE triple constraints whose equals is structural; canonicalize with an
     * identity-based index so distinct SORBE copies are distinguished. */
    private Map<TripleConstraint, Integer> tcIndex(List<TripleExprForValidation> teVals) {
        IdentityHashMap<TripleConstraint, Integer> index = new IdentityHashMap<>();
        for (TripleExprForValidation teVal : teVals)
            for (TripleConstraint tc : teVal.getTripleConstraints())
                index.put(tc, index.size());
        return index;
    }

    private Map<Triple, Integer> canonical(Map<Triple, TripleConstraint> matching,
                                           Map<TripleConstraint, Integer> tcIndex) {
        Map<Triple, Integer> result = new HashMap<>();
        matching.forEach((t, tc) -> result.put(t, tcIndex.get(tc)));
        return result;
    }

    private TripleExprForValidation exprForValidation(String shapeDecl) {
        ShexSchema schema = Shex.schemaFromString(PFX + shapeDecl);
        ValidationContext vCxt = new ValidationContext(schema, GraphFactory.createDefaultGraph(),
                Map.of(), true, true);
        ShapeExpr se = schema.getShapes().get(0).getShapeExpr();
        Shape shape = (Shape) se;
        return vCxt.getExprForValidation(shape.getTripleExpr());
    }

    private Set<Triple> triples(int nP, int nQ) {
        Node s = NodeFactory.createURI("http://dt.ex/s");
        Node p = NodeFactory.createURI("http://dt.ex/p");
        Node q = NodeFactory.createURI("http://dt.ex/q");
        Set<Triple> triples = new HashSet<>();
        for (int i = 0; i < nP; i++)
            triples.add(Triple.create(s, p, NodeFactory.createLiteralString("p" + i)));
        for (int i = 0; i < nQ; i++)
            triples.add(Triple.create(s, q, NodeFactory.createLiteralString("q" + i)));
        return triples;
    }

    /** Replicates the production pre-matching construction: with every triple, associate the
     * same-predicate triple constraints of all the expressions (value filtering is irrelevant
     * here since all value expressions are '.'). */
    private Map<Triple, List<TripleConstraint>> merge(List<TripleExprForValidation> teVals,
                                                      Set<Triple> triples) {
        Map<Triple, List<TripleConstraint>> preMatching = new HashMap<>();
        triples.forEach(t -> preMatching.put(t, new ArrayList<>()));
        for (TripleExprForValidation teVal : teVals) {
            Map<Triple, List<TripleConstraint>> pm = teVal.getPredicateBasedPreMatching(triples);
            pm.forEach((triple, list) -> preMatching.get(triple).addAll(list));
        }
        return preMatching;
    }
}
