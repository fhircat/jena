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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shex.Shex;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.expressions.TripleConstraint;
import org.apache.jena.shex.reporting.Report;
import org.apache.jena.shex.reporting.Reporter;
import org.apache.jena.shex.reporting.ShexValidationReport;
import org.apache.jena.shex.reporting.SimpleReport;
import org.apache.jena.shex.sys.ShexValidatorImpl;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Empirical probes characterizing the matching-search (partition exploration) behaviour of the
 * validator in {@link ShapeExprEval} / {@link TripleExprEval} / {@link MatchingsIterator}.
 *
 * These are not regression tests of desired outcomes; they pin down the semantics and the
 * combinatorial cost of the current algorithm, as ground truth for the planned search-space
 * optimization (min-cardinality / co-occurrence aware pruning of triple-to-tripleConstraint
 * assignments). See jena-shex/docs/matching-search-optimization.md.
 *
 * Not part of the default surefire run (which includes only TS_*); run with:
 *   mvn -pl jena-shex test -Dtest=MatchingSearchProbesTest -Drat.skip=true
 */
public class MatchingSearchProbesTest {

    private static final String PFX = "PREFIX : <http://probe.ex/>\n";

    /** A Reporter that counts how many candidate matchings the validator fully checks
     * (each call to informCandidateMatchingConformance is one Bag construction + interval computation). */
    private static class CountingReporter implements Reporter {
        final AtomicLong checkedMatchings;
        private ShexStatus status = ShexStatus.nonconformant;

        CountingReporter() { this(new AtomicLong()); }
        private CountingReporter(AtomicLong counter) { this.checkedMatchings = counter; }

        @Override public Report getReport() { return new SimpleReport(status, ""); }
        @Override public Reporter createRoot(Node node, ShapeExprRef expr) { return new CountingReporter(checkedMatchings); }
        @Override public Reporter createChild(Node node, Expression expr, Set<Triple> neigh) { return new CountingReporter(checkedMatchings); }
        @Override public void setReferenceTo(Report report, String additionalMessage) {}
        @Override public void setResult(ShexStatus s) { this.status = s; }
        @Override public void addInfo(String message, Object details) {}
        @Override public boolean isValidateOnly() { return true; }

        @Override
        public void informCandidateMatchingConformance(boolean isConformant,
                                                       Map<Triple, TripleConstraint> matching,
                                                       MatchingNotSatisfiedReason reasonIfNonConformant) {
            checkedMatchings.incrementAndGet();
        }
    }

    private record Outcome(boolean conforms, long checkedMatchings, long elapsedMillis) {}

    private static Outcome validate(String shexc, String turtle, String shapeLabel, String focus) {
        ShexSchema schema = Shex.schemaFromString(PFX + shexc);
        Graph graph = GraphFactory.createDefaultGraph();
        RDFParser.create().fromString(PFX + turtle).lang(Lang.TURTLE).parse(graph);

        ShexValidatorImpl validator = new ShexValidatorImpl(Collections.emptyMap());
        CountingReporter reporter = new CountingReporter();
        validator.setReporter(reporter);

        long t0 = System.nanoTime();
        ShexValidationReport report = validator.validate(graph, schema,
                NodeFactory.createURI("http://probe.ex/" + shapeLabel),
                NodeFactory.createURI("http://probe.ex/" + focus));
        long elapsed = (System.nanoTime() - t0) / 1_000_000;
        return new Outcome(report.conforms(), reporter.checkedMatchings.get(), elapsed);
    }

    // ------------------------------------------------------------------------------------------
    // Probe 1: triple constraints sharing a predicate across OneOf branches.
    // Schema:  :S { :p . {1,2} | :p . + ; :q . }
    // Data: n distinct :p triples, no :q triple. Nonconformant for n >= 3.
    //
    // Every :p triple gets two candidate TCs (both branches). The historical MatchingsIterator
    // enumerated the full Cartesian product: 2^n matchings, all failing the interval check
    // (65536 checked matchings and 166ms at n=16). The feasibility analysis instead refutes the
    // second branch's :p constraint during the arc-consistency pre-pass (assigning any triple to
    // it requires an occupied :q constraint, but no :q triple exists), leaving all triples on
    // the first branch's constraint, whose {1,2} maximum is exceeded: zero matchings reach the
    // interval check.
    // ------------------------------------------------------------------------------------------

    private static final String BLOWUP_SCHEMA = ":S { :p . {1,2} | :p . + ; :q . }";

    private static String nPTriples(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++)
            sb.append(":s :p \"v").append(i).append("\" .\n");
        return sb.toString();
    }

    @Test
    public void probe1_sharedPredicateOneOf_isPrunedWithoutEnumeration() {
        System.out.println("probe1: schema " + BLOWUP_SCHEMA + ", data = n x ':s :p ...', no :q");
        for (int n : new int[] { 4, 8, 12, 16, 24 }) {
            Outcome o = validate(BLOWUP_SCHEMA, nPTriples(n), "S", "s");
            System.out.printf("probe1: n=%2d  checkedMatchings=%6d (was 2^n=%8d)  %d ms%n",
                    n, o.checkedMatchings(), 1L << n, o.elapsedMillis());
            assertFalse("n=" + n + " should be nonconformant", o.conforms());
            assertEquals("no matching survives the feasibility analysis for n=" + n,
                    0L, o.checkedMatchings());
        }
        // Positive control: n = 2 fits the {1,2} branch, hence conformant with a single matching.
        Outcome positive = validate(BLOWUP_SCHEMA, nPTriples(2), "S", "s");
        assertTrue("n=2 conforms via the first branch", positive.conforms());
        assertEquals("exactly one matching checked for n=2", 1L, positive.checkedMatchings());
    }

    // ------------------------------------------------------------------------------------------
    // Probe 2: the motivating Person example ( TC1+ | TC2+ ; TC3 ) with *distinct* predicates.
    // Each triple has exactly one candidate TC, so even the historical algorithm checked a
    // single matching. The feasibility analysis now refutes it upfront: a :givenName triple
    // requires an occupied :familyName constraint (no such triple), and the :name branch then
    // requires the :givenName constraints to be unoccupied. The class loses all candidates: no
    // matching exists (a value-matching triple can never be left unmatched).
    // ------------------------------------------------------------------------------------------

    @Test
    public void probe2_distinctPredicates_refutedByArcConsistency() {
        String schema = ":Person { :name . + | :givenName . + ; :familyName . }";
        String data = ":s :givenName \"Eric\" .\n:s :givenName \"G\" .\n"; // no familyName
        Outcome o = validate(schema, data, "Person", "s");
        System.out.printf("probe2: checkedMatchings=%d conforms=%b%n", o.checkedMatchings(), o.conforms());
        assertFalse("givenName without familyName should be nonconformant", o.conforms());
        assertEquals("refuted before any matching is checked", 0L, o.checkedMatchings());

        // Positive controls: each OneOf branch on its own conforms.
        Outcome nameOnly = validate(schema, ":s :name \"Eric Prud'hommeaux\" .\n", "Person", "s");
        assertTrue("name alone conforms", nameOnly.conforms());
        Outcome givenFamily = validate(schema,
                ":s :givenName \"Eric\" .\n:s :familyName \"Prud'hommeaux\" .\n", "Person", "s");
        assertTrue("givenName + familyName conforms", givenFamily.conforms());
    }

    // ------------------------------------------------------------------------------------------
    // Probe 3: OneOf exclusivity does not survive repetition.
    // (:a . | :b .)   rejects {a,b}: the bag must match exactly one alternative.
    // (:a . | :b .)+  accepts {a,b}: each iteration picks a branch independently.
    // Consequence for the optimization: a normal-form compilation must not treat OneOf
    // exclusivity as a global constraint when the OneOf is under a repetition; only the
    // monotone part (occ(TC in branch) => branch's own minimum requirements) survives.
    // ------------------------------------------------------------------------------------------

    @Test
    public void probe3_oneOfExclusivity_vsRepetition() {
        String data = ":s :a \"x\" .\n:s :b \"y\" .\n";
        Outcome exclusive = validate(":S { :a . | :b . }", data, "S", "s");
        Outcome repeated  = validate(":S { (:a . | :b .)+ }", data, "S", "s");
        System.out.printf("probe3: (a|b) conforms=%b ; (a|b)+ conforms=%b%n",
                exclusive.conforms(), repeated.conforms());
        assertFalse("unrepeated OneOf is exclusive", exclusive.conforms());
        assertTrue("repeated OneOf accepts mixed branches", repeated.conforms());
    }

    // ------------------------------------------------------------------------------------------
    // Probe 4: EachOf under repetition couples counts (Parikh-style), beyond per-TC intervals.
    // (:a . ; :b .)+ requires #a == #b. An occupancy (which-TCs-are-nonempty) abstraction is
    // only a NECESSARY condition; the interval computation must remain the final verifier.
    // ------------------------------------------------------------------------------------------

    @Test
    public void probe4_repeatedEachOf_couplesCounts() {
        String schema = ":S { (:a . ; :b .)+ }";
        Outcome balanced   = validate(schema, ":s :a \"1\" .\n:s :b \"1\" .\n", "S", "s");
        Outcome unbalanced = validate(schema, ":s :a \"1\" .\n:s :a \"2\" .\n:s :b \"1\" .\n", "S", "s");
        System.out.printf("probe4: balanced conforms=%b ; unbalanced conforms=%b%n",
                balanced.conforms(), unbalanced.conforms());
        assertTrue("1 a + 1 b conforms", balanced.conforms());
        assertFalse("2 a + 1 b must fail: iterations pair a with b", unbalanced.conforms());
    }

    // ------------------------------------------------------------------------------------------
    // Probe 5: EXTRA does not license dropping a triple that value-matches a TC.
    // Approved shexTest case 1val2IRIREFExtra1_fail-iri2 pins this:
    //   <S> EXTRA <p1> { <p1> [<o1> <o2>] } on { <s1> <p1> <o1>, <o2> }  is a ValidationFailure.
    // Hence forced assignment of every value-matching triple (MatchingsIterator has no
    // "unmatched" option) is spec-correct, and the planned candidate-set data model must NOT
    // offer a drop/unmatched alternative for EXTRA predicates. EXTRA only excuses unmatched
    // triples that match no TC (probe 5a).
    // ------------------------------------------------------------------------------------------

    @Test
    public void probe5_extraDoesNotLicenseDroppingValueMatchingTriples() {
        String data = ":s :p :o1 .\n:s :p :o2 .\n";
        Outcome valueFailing  = validate(":S EXTRA :p { :p [:o1] }", data, "S", "s");
        Outcome valueMatching = validate(":S EXTRA :p { :p [:o1 :o2] }", data, "S", "s");
        System.out.printf("probe5: extra-absorbs-value-failing conforms=%b ; surplus-value-matching conforms=%b%n",
                valueFailing.conforms(), valueMatching.conforms());
        assertTrue("EXTRA absorbs the value-failing :o2 triple", valueFailing.conforms());
        assertFalse("both triples value-match the single-occurrence TC: must fail", valueMatching.conforms());
    }
}
