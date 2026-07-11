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

import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.TripleConstraint;
import org.apache.jena.shex.expressions.TripleExpr;

import java.util.*;

/**
 * Iterates over the matchings (maps from triples to triple constraints) that can possibly
 * satisfy the given triple expressions, in place of the plain Cartesian product computed by
 * {@link MatchingsIterator}.
 *
 * <p>Produces a subset of {@link MatchingsIterator}'s output that contains every
 * <em>valid</em> matching: only matchings refuted by {@link TripleExprFeasibility} are skipped,
 * and those are never accepted by the exact interval verification downstream. Hence replacing
 * {@link MatchingsIterator} with this iterator preserves the set of accepted matchings
 * (soundness and completeness of validation), only the enumeration order and the amount of
 * discarded work differ.
 *
 * <p>Search organisation, from cheap to expensive:
 * <ol>
 * <li><b>Triple classes</b>: triples with the same candidate constraint set are grouped; a class
 *     of n triples over k candidates has C(n+k-1, k-1) count distributions instead of k^n
 *     assignments.</li>
 * <li><b>Arc consistency</b>: a candidate constraint is removed from a class if committing a
 *     single triple of the class to it is already infeasible; removals are iterated to fixpoint.
 *     (This generalises the removal of candidates whose value expression fails, performed
 *     upstream, to cardinality/co-occurrence infeasibility.) If a class loses all candidates,
 *     no matching exists: a triple whose predicate is mentioned can never be left unmatched
 *     (cf. shexTest 1val2IRIREFExtra1_fail-iri2), so the iterator is empty.</li>
 * <li><b>Distribution search</b>: depth-first over classes (fewest candidates first), assigning
 *     each class a count vector over its candidates, bounded by the constraints' static maximums
 *     and pruned by {@link TripleExprFeasibility#feasible} after each class.</li>
 * <li><b>Expansion</b>: each surviving complete bag is expanded into all the concrete matchings
 *     that realise it (multiset permutations per class, Cartesian across classes), as required
 *     for the downstream checks that depend on which concrete triples are matched
 *     (EXTENDS constraints on splits, semantic actions).</li>
 * </ol>
 */
/*package*/ class FeasibleMatchingsIterator implements Iterator<Map<Triple, TripleConstraint>> {

    /** A group of triples with identical candidate constraint sets. */
    private static class TripleClass {
        final List<Triple> triples = new ArrayList<>();
        int[] candidates;               // indexes into allTripleConstraints, sorted
        int size() { return triples.size(); }
    }

    private final List<TripleConstraint> allTripleConstraints;
    private final TripleExprFeasibility feasibility;
    private final List<TripleClass> classes = new ArrayList<>();
    private boolean infeasible = false;

    // --- Distribution search state ---
    /** counts[level] = count vector over classes.get(level).candidates; null above current level. */
    private int[][] counts;
    /** lo[tcIdx] = number of triples committed to the constraint by classes above the current level. */
    private int[] lo;
    /** potential[tcIdx] = number of triples of not-yet-assigned classes having the constraint as candidate. */
    private int[] potential;
    /** Scratch array for feasibility tests: hi = lo + potential. */
    private int[] hiScratch;
    /** Current search level: classes 0..level-1 have committed count vectors. -1 before start. */
    private int level;

    // --- Expansion state (non-null while expanding the current complete bag) ---
    /** perms[i] = assignment of classes.get(i).triples to candidate indexes, as a permutation of
     * the multiset defined by counts[i]; advanced odometer-style. */
    private int[][] perms;

    private Map<Triple, TripleConstraint> nextMatching;   // lookahead
    private boolean exhausted = false;

    /*package*/ FeasibleMatchingsIterator(Map<Triple, List<TripleConstraint>> preMatching,
                                          Collection<TripleExprForValidation> exprsToBeMatched) {
        // Fixed order and identity index of all triple constraints of the hierarchy
        this.allTripleConstraints = new ArrayList<>();
        List<TripleExpr> roots = new ArrayList<>(exprsToBeMatched.size());
        for (TripleExprForValidation teVal : exprsToBeMatched) {
            allTripleConstraints.addAll(teVal.getTripleConstraints());
            roots.add(teVal.getRelevantExpression());
        }
        IdentityHashMap<TripleConstraint, Integer> tcIndex = new IdentityHashMap<>();
        for (int i = 0; i < allTripleConstraints.size(); i++)
            tcIndex.put(allTripleConstraints.get(i), i);

        this.feasibility = new TripleExprFeasibility(roots, allTripleConstraints);

        // Group the triples into classes by candidate set
        Map<String, TripleClass> classByKey = new HashMap<>();
        preMatching.forEach((triple, tcs) -> {
            int[] cands = tcs.stream().mapToInt(tcIndex::get).sorted().toArray();
            String key = Arrays.toString(cands);
            TripleClass cls = classByKey.computeIfAbsent(key, k -> {
                TripleClass c = new TripleClass();
                c.candidates = cands;
                classes.add(c);
                return c;
            });
            cls.triples.add(triple);
        });

        // Most-constrained classes first: fewer candidates, then more triples
        classes.sort(Comparator.<TripleClass>comparingInt(c -> c.candidates.length)
                .thenComparing(Comparator.<TripleClass>comparingInt(TripleClass::size).reversed()));

        int nTcs = allTripleConstraints.size();
        this.lo = new int[nTcs];
        this.potential = new int[nTcs];
        this.hiScratch = new int[nTcs];
        for (TripleClass cls : classes)
            for (int cand : cls.candidates)
                potential[cand] += cls.size();

        enforceArcConsistency();

        this.counts = new int[classes.size()][];
        this.level = 0;
        computeNext();
    }

    /** Removes from each class the candidates to which no triple of the class can be assigned
     * in any accepted completion; iterates to fixpoint since removals lower the upper bounds. */
    private void enforceArcConsistency() {
        boolean changed = true;
        while (changed && ! infeasible) {
            changed = false;
            for (TripleClass cls : classes) {
                int keep = 0;
                int[] cands = cls.candidates;
                for (int ci = 0; ci < cands.length; ci++) {
                    int cand = cands[ci];
                    lo[cand] = 1;
                    System.arraycopy(potential, 0, hiScratch, 0, potential.length);
                    boolean supported = feasibility.feasible(lo, hiScratch);
                    lo[cand] = 0;
                    if (supported) {
                        cands[keep++] = cand;
                    } else {
                        potential[cand] -= cls.size();
                        changed = true;
                    }
                }
                if (keep < cands.length) {
                    cls.candidates = Arrays.copyOf(cands, keep);
                    if (keep == 0) {
                        // A triple whose predicate is mentioned cannot be left unmatched.
                        infeasible = true;
                        return;
                    }
                }
            }
        }
    }

    @Override
    public boolean hasNext() {
        return nextMatching != null;
    }

    @Override
    public Map<Triple, TripleConstraint> next() {
        if (nextMatching == null)
            throw new NoSuchElementException();
        Map<Triple, TripleConstraint> result = nextMatching;
        computeNext();
        return result;
    }

    // ------------------------------------------------------------------------------------------
    // Lookahead computation
    // ------------------------------------------------------------------------------------------

    private void computeNext() {
        nextMatching = null;
        if (infeasible || exhausted)
            return;

        // If currently expanding a bag, try to produce its next matching
        if (perms != null) {
            if (advancePerms()) {
                nextMatching = currentMatching();
                return;
            }
            perms = null;                       // bag exhausted: resume the distribution search
            if (! stepBack())
                return;
        }

        // Depth-first search for the next complete feasible bag
        while (true) {
            if (level == classes.size()) {      // complete bag: start expanding it
                initPerms();
                nextMatching = currentMatching();
                return;
            }
            boolean have = (counts[level] == null)
                    ? firstComposition(level)
                    : advanceComposition(level);
            while (have && ! feasibleNow())
                have = advanceComposition(level);
            if (have)
                level++;                        // descend
            else if (! stepBack())              // this level exhausted
                return;
        }
    }

    /** Moves up one level; the search loop then advances that level's composition.
     * Returns false when the whole search space is exhausted. */
    private boolean stepBack() {
        level--;
        if (level < 0) {
            exhausted = true;
            return false;
        }
        return true;
    }

    /** Whether the current partial assignment can still be completed (necessary condition). */
    private boolean feasibleNow() {
        for (int i = 0; i < hiScratch.length; i++)
            hiScratch[i] = lo[i] + potential[i];
        return feasibility.feasible(lo, hiScratch);
    }

    // ------------------------------------------------------------------------------------------
    // Compositions: count vectors c over a class's candidates with sum = class size and
    // c[i] <= cap(candidate i). Enumerated in decreasing lexicographic order.
    // The composition is "committed": its counts are added into lo, and the class's contribution
    // is removed from potential, as soon as it is produced.
    // ------------------------------------------------------------------------------------------

    /** How many triples of a class of the given size can still go to the candidate constraint:
     * bounded by the constraint's static maximum (clamped to the class size, also avoiding
     * overflow when summing caps of unbounded constraints). */
    private int cap(int candIdx, int classSize) {
        long sm = feasibility.staticMax(candIdx);
        long remaining = sm - lo[candIdx];
        return (int) Math.max(0, Math.min(remaining, classSize));
    }

    /** Produces and commits the lexicographically greatest composition. */
    private boolean firstComposition(int lvl) {
        TripleClass cls = classes.get(lvl);
        // remove the class contribution from potential before computing caps against lo
        for (int cand : cls.candidates)
            potential[cand] -= cls.size();
        int[] c = new int[cls.candidates.length];
        int remaining = cls.size();
        for (int i = 0; i < c.length && remaining > 0; i++) {
            c[i] = Math.min(cap(cls.candidates[i], cls.size()), remaining);
            remaining -= c[i];
        }
        if (remaining > 0) {                        // class does not fit at all
            for (int cand : cls.candidates)
                potential[cand] += cls.size();
            return false;
        }
        counts[lvl] = c;
        for (int i = 0; i < c.length; i++)
            lo[cls.candidates[i]] += c[i];
        return true;
    }

    /** Un-commits then advances the composition at the level and re-commits the new one.
     * On exhaustion, restores the class's contribution to potential, clears counts[lvl] and
     * returns false. */
    private boolean advanceComposition(int lvl) {
        TripleClass cls = classes.get(lvl);
        uncommitCounts(lvl);
        if (nextComposition(lvl)) {
            int[] c = counts[lvl];
            for (int i = 0; i < c.length; i++)
                lo[cls.candidates[i]] += c[i];
            return true;
        }
        for (int cand : cls.candidates)
            potential[cand] += cls.size();
        counts[lvl] = null;
        return false;
    }

    /** Advances counts[lvl] to the next composition (not committed). Assumes counts not committed. */
    private boolean nextComposition(int lvl) {
        TripleClass cls = classes.get(lvl);
        int[] c = counts[lvl];
        int k = c.length;
        for (int i = k - 2; i >= 0; i--) {
            if (c[i] == 0)
                continue;
            // capacity to the right of i, and current sum to the right of i
            int rightCap = 0, rightSum = 0;
            for (int j = i + 1; j < k; j++) {
                rightCap += cap(cls.candidates[j], cls.size());
                rightSum += c[j];
            }
            if (rightCap >= rightSum + 1) {
                c[i]--;
                int remaining = rightSum + 1;
                for (int j = i + 1; j < k; j++) {
                    c[j] = Math.min(cap(cls.candidates[j], cls.size()), remaining);
                    remaining -= c[j];
                }
                return true;
            }
        }
        return false;
    }

    /** Removes the committed counts of the level from lo (potential untouched). */
    private void uncommitCounts(int lvl) {
        TripleClass cls = classes.get(lvl);
        int[] c = counts[lvl];
        for (int i = 0; i < c.length; i++)
            lo[cls.candidates[i]] -= c[i];
    }

    // ------------------------------------------------------------------------------------------
    // Expansion of the current bag into concrete matchings
    // ------------------------------------------------------------------------------------------

    /** Initializes perms[i] as the lexicographically smallest multiset permutation for class i. */
    private void initPerms() {
        perms = new int[classes.size()][];
        for (int i = 0; i < classes.size(); i++) {
            TripleClass cls = classes.get(i);
            int[] p = new int[cls.size()];
            int at = 0;
            for (int ci = 0; ci < cls.candidates.length; ci++)
                for (int r = 0; r < counts[i][ci]; r++)
                    p[at++] = cls.candidates[ci];
            // candidates are sorted, hence p is sorted: the smallest permutation
            perms[i] = p;
        }
    }

    /** Advances the perms odometer. Returns false when all permutations have been produced;
     * the first call after {@link #initPerms()} must not advance (handled by callers producing
     * the initial matching before calling this). */
    private boolean advancePerms() {
        for (int i = 0; i < perms.length; i++) {
            if (nextPermutation(perms[i]))
                return true;
            // wrapped: reset to smallest and carry
            Arrays.sort(perms[i]);
        }
        return false;
    }

    /** Standard next lexicographic permutation of a multiset; false if the array is the last one. */
    private static boolean nextPermutation(int[] a) {
        int i = a.length - 2;
        while (i >= 0 && a[i] >= a[i + 1])
            i--;
        if (i < 0)
            return false;
        int j = a.length - 1;
        while (a[j] <= a[i])
            j--;
        int t = a[i]; a[i] = a[j]; a[j] = t;
        for (int l = i + 1, r = a.length - 1; l < r; l++, r--) {
            t = a[l]; a[l] = a[r]; a[r] = t;
        }
        return true;
    }

    private Map<Triple, TripleConstraint> currentMatching() {
        Map<Triple, TripleConstraint> matching = new HashMap<>();
        for (int i = 0; i < classes.size(); i++) {
            TripleClass cls = classes.get(i);
            for (int t = 0; t < cls.size(); t++)
                matching.put(cls.triples.get(t), allTripleConstraints.get(perms[i][t]));
        }
        return matching;
    }
}
