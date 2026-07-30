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

import org.apache.jena.shex.expressions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A sound refutation test for partially-determined bags over the triple constraints of one or
 * several SORBE triple expressions.
 *
 * <p>A <em>partial bag</em> assigns to every triple constraint {@code tc} an interval
 * {@code [lo(tc), hi(tc)]}: {@code lo(tc)} triples are already committed to {@code tc}, and at
 * most {@code hi(tc) - lo(tc)} additional triples could still be assigned to it. A
 * <em>completion</em> is any exact bag {@code c} with {@code lo <= c <= hi} pointwise.
 * (The set of completions considered here is an over-approximation of the bags actually reachable
 * during search, which is what makes refutation sound.)
 *
 * <p>{@link #feasible(int[], int[])} returns {@code false} only if <em>no</em> completion is
 * accepted by every one of the expressions (conjunction; used for the EXTENDS hierarchy where
 * each supertype's triple expression must accept its share of the same matching, hence the
 * conjunction acts as a virtual EachOf over disjoint triple-constraint alphabets).
 * It may return {@code true} for infeasible states: it is a <em>necessary</em> condition, not a
 * decision procedure. Callers must verify complete bags with the exact
 * {@link IntervalComputation}. This division of labour keeps the overall validation algorithm
 * sound and complete while allowing aggressive search-space pruning.
 *
 * <p>The predicate is computed compositionally over the SORBE tree, in two modes:
 * <ul>
 * <li><b>exact mode</b> ({@code fExact}): the sub-expression must be matched exactly once by its
 *     completion slice. OneOf alternatives are exclusive: choosing branch {@code i} requires all
 *     other branches to be completable to zero ({@code lo = 0} on their constraints).</li>
 * <li><b>iterated mode</b> ({@code fIter}): the sub-expression is matched by an unknown number
 *     {@code q >= 1} of iterations whose bags sum to the completion slice. Only the
 *     <em>monotone</em> consequences survive summation: OneOf exclusivity is dropped (different
 *     iterations may choose different branches), and per-constraint upper bounds are dropped
 *     ({@code q} is unbounded), but EachOf co-occurrence remains at the occupancy level: if any
 *     constraint of an EachOf component is occupied then every non-nullable sibling component
 *     must be occupiable.</li>
 * </ul>
 * The auxiliary predicate {@code once} checks that a single non-empty iteration is achievable
 * within the {@code hi} bounds, as required by {@code +} (and by {@code *} when the slice is not
 * completable to zero).
 *
 * <p>Soundness sketch (structural induction; the completion {@code c} is fixed and each case
 * shows the predicate holds whenever {@code c}'s slice is accepted):
 * <ul>
 * <li>{@code TC[m,n]} exact: the count {@code k} of {@code c} on {@code TC} satisfies
 *     {@code m <= k <= n}, and {@code lo <= k <= hi}, hence {@code lo <= n} and {@code hi >= m}.</li>
 * <li>{@code EachOf} (both modes): sibling alphabets are disjoint (single-occurrence), so
 *     acceptance decomposes componentwise and the conjunction of the children's conditions is
 *     necessary.</li>
 * <li>{@code OneOf} exact: acceptance selects one branch; the other branches' slices are zero,
 *     hence their {@code lo} must be zero.</li>
 * <li>{@code OneOf} iterated: each branch is either never selected (slice zero, {@code lo = 0})
 *     or selected by at least one iteration (its condition in iterated mode is necessary).</li>
 * <li>{@code E?} / {@code E*}: either the slice is zero ({@code lo = 0} on all constraints of
 *     {@code E}) or at least one (non-empty, w.l.o.g.) iteration exists, making the iterated
 *     condition and {@code once} necessary.</li>
 * <li>{@code E+}: SORBE guarantees {@code E} is not nullable, so at least one non-empty
 *     iteration exists.</li>
 * <li>{@code TC[m,n]} iterated: the count is a sum of {@code q >= 1} per-iteration counts each in
 *     {@code [m,n]}; if {@code m >= 1} the total is at least {@code m}, hence {@code hi >= m};
 *     if {@code n == 0} the total is zero, hence {@code lo == 0}.</li>
 * </ul>
 *
 * <p>What the predicate deliberately does <em>not</em> capture (and why completion verification
 * remains necessary): count coupling between constraints of a repeated group (e.g.
 * {@code (a . ; b .)+} requires #a = #b, but any occupied state is deemed feasible here), and
 * divisibility constraints (e.g. {@code (a .){2,2}} under {@code *} accepts only even counts).
 * These are Parikh-image constraints of the bag language that are not expressible as independent
 * per-constraint intervals plus occupancy implications.
 */
/*package*/ class TripleExprFeasibility {

    /** Indexed triple constraints, in a fixed order shared with the caller. */
    private final List<TripleConstraint> tripleConstraints;
    /** The roots of the SORBE trees (one per triple expression in the EXTENDS hierarchy). */
    private final List<TripleExpr> roots;

    /** Per node of the SORBE trees: the indexes (in {@link #tripleConstraints}) of the triple
     * constraints occurring in that subtree. Identity-based to distinguish SORBE copies. */
    private final java.util.IdentityHashMap<TripleExpr, int[]> subtreeTcIndexes = new java.util.IdentityHashMap<>();

    /** The maximum number of triples assignable to each triple constraint in any accepted bag:
     * the constraint's declared max, or unbounded if some ancestor is a repetition. */
    private final int[] staticMax;

    // State of the feasibility test being evaluated. Single-threaded use only.
    private int[] lo;
    private int[] hi;

    /** Index of each triple constraint in the caller-supplied order. Identity-based to
     * distinguish SORBE copies, which can be equal wrt Object#equals. */
    private final java.util.IdentityHashMap<TripleConstraint, Integer> tcIndex = new java.util.IdentityHashMap<>();

    /*package*/ TripleExprFeasibility(List<TripleExpr> sorbeRoots, List<TripleConstraint> tripleConstraints) {
        this.roots = sorbeRoots;
        this.tripleConstraints = tripleConstraints;
        this.staticMax = new int[tripleConstraints.size()];
        for (int i = 0; i < tripleConstraints.size(); i++)
            tcIndex.put(tripleConstraints.get(i), i);
        for (TripleExpr root : roots)
            index(root, false);
    }

    /*package*/ int tripleConstraintCount() {
        return tripleConstraints.size();
    }

    /** An upper bound on the count of the i-th triple constraint in any accepted bag
     * ({@link Cardinality#UNBOUNDED} if under a repetition). */
    /*package*/ int staticMax(int tcIdx) {
        return staticMax[tcIdx];
    }

    /** Whether some completion c with {@code lo <= c <= hi} (pointwise) can be accepted by all
     * the expressions. {@code false} is definitive (no completion is accepted); {@code true} is
     * not (the exact {@link IntervalComputation} decides complete bags). */
    /*package*/ boolean feasible(int[] lo, int[] hi) {
        this.lo = lo;
        this.hi = hi;
        try {
            for (TripleExpr root : roots) {
                if (! fExact(root))
                    return false;
            }
            return true;
        } finally {
            this.lo = null;
            this.hi = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Precomputation
    // ------------------------------------------------------------------------------------------

    /** Collects the subtree triple-constraint indexes and static maximums.
     * @param underRepetition whether some ancestor repeats this subtree an unbounded number of times */
    private int[] index(TripleExpr expr, boolean underRepetition) {
        int[] result;
        if (expr instanceof TripleConstraint tc) {
            int idx = tcIndex.get(tc);
            staticMax[idx] = underRepetition ? Cardinality.UNBOUNDED : 1;
            result = new int[]{ idx };
        } else if (expr instanceof TripleExprEmpty) {
            result = new int[0];
        } else if (expr instanceof TripleExprCardinality card) {
            boolean repeats = underRepetition || card.max() == Cardinality.UNBOUNDED || card.max() > 1;
            result = index(card.getSubExpr(), repeats);
            if (card.getSubExpr() instanceof TripleConstraint tc) {
                int idx = tcIndex.get(tc);
                staticMax[idx] = underRepetition ? Cardinality.UNBOUNDED : card.max();
            }
        } else if (expr instanceof EachOf eachOf) {
            result = indexChildren(eachOf.getTripleExprs(), underRepetition);
        } else if (expr instanceof OneOf oneOf) {
            result = indexChildren(oneOf.getTripleExprs(), underRepetition);
        } else {
            throw new IllegalArgumentException("Unexpected expression in SORBE form: " + expr.getClass());
        }
        subtreeTcIndexes.put(expr, result);
        return result;
    }

    private int[] indexChildren(List<TripleExpr> children, boolean underRepetition) {
        List<int[]> collected = new ArrayList<>(children.size());
        int total = 0;
        for (TripleExpr child : children) {
            int[] childIndexes = index(child, underRepetition);
            collected.add(childIndexes);
            total += childIndexes.length;
        }
        int[] result = new int[total];
        int at = 0;
        for (int[] childIndexes : collected) {
            System.arraycopy(childIndexes, 0, result, at, childIndexes.length);
            at += childIndexes.length;
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------
    // The predicate
    // ------------------------------------------------------------------------------------------

    /** Necessary condition for some completion slice of the subtree to be accepted exactly once. */
    private boolean fExact(TripleExpr expr) {
        if (expr instanceof TripleConstraint tc)
            return boundsAllow(tc, 1, 1);
        if (expr instanceof TripleExprEmpty)
            return true;
        if (expr instanceof EachOf eachOf) {
            for (TripleExpr sub : eachOf.getTripleExprs())
                if (! fExact(sub))
                    return false;
            return true;
        }
        if (expr instanceof OneOf oneOf) {
            List<TripleExpr> branches = oneOf.getTripleExprs();
            for (int i = 0; i < branches.size(); i++) {
                if (! fExact(branches.get(i)))
                    continue;
                boolean othersCanBeEmpty = true;
                for (int j = 0; j < branches.size(); j++) {
                    if (j != i && ! zeroPossible(branches.get(j))) {
                        othersCanBeEmpty = false;
                        break;
                    }
                }
                if (othersCanBeEmpty)
                    return true;
            }
            return false;
        }
        if (expr instanceof TripleExprCardinality card) {
            TripleExpr sub = card.getSubExpr();
            int min = card.min();
            int max = card.max();
            if (sub instanceof TripleConstraint tc)
                return boundsAllow(tc, min, max);
            if (max == 0)                                       // {0,0}
                return zeroPossible(sub);
            if (min == 0 && max == 1)                           // ?
                return zeroPossible(sub) || fExact(sub);
            if (min == 0)                                       // *
                return zeroPossible(sub) || (fIter(sub) && once(sub));
            if (min == 1 && max == Cardinality.UNBOUNDED)       // +
                return fIter(sub) && once(sub);
            // Other cardinalities on non-constraints do not occur in SORBE expressions
            throw new IllegalArgumentException("Cardinality " + card.getCardinality()
                    + " on a non-constraint in SORBE form");
        }
        throw new IllegalArgumentException("Unexpected expression in SORBE form: " + expr.getClass());
    }

    /** Necessary condition for some completion slice of the subtree to be a sum of q >= 1 bags
     * each accepted by the subtree. Monotone weakening of {@link #fExact}: OneOf exclusivity and
     * upper bounds are dropped; EachOf co-occurrence is kept at the occupancy level. */
    private boolean fIter(TripleExpr expr) {
        if (expr instanceof TripleConstraint tc)
            return hi[idx(tc)] >= 1;
        if (expr instanceof TripleExprEmpty)
            return true;
        if (expr instanceof EachOf eachOf) {
            for (TripleExpr sub : eachOf.getTripleExprs())
                if (! fIter(sub))
                    return false;
            return true;
        }
        if (expr instanceof OneOf oneOf) {
            for (TripleExpr branch : oneOf.getTripleExprs())
                if (! zeroPossible(branch) && ! fIter(branch))
                    return false;
            return true;
        }
        if (expr instanceof TripleExprCardinality card) {
            TripleExpr sub = card.getSubExpr();
            int min = card.min();
            int max = card.max();
            if (sub instanceof TripleConstraint tc)
                // Sum of q >= 1 counts each in [min,max]: at least min if min >= 1; zero if max == 0.
                return (min == 0 || hi[idx(tc)] >= min)
                        && (max > 0 || lo[idx(tc)] == 0);
            if (max == 0)                                       // {0,0}
                return zeroPossible(sub);
            if (min == 0)                                       // ? or *
                return zeroPossible(sub) || fIter(sub);
            if (min == 1 && max == Cardinality.UNBOUNDED)       // +
                return fIter(sub) && once(sub);
            throw new IllegalArgumentException("Cardinality " + card.getCardinality()
                    + " on a non-constraint in SORBE form");
        }
        throw new IllegalArgumentException("Unexpected expression in SORBE form: " + expr.getClass());
    }

    /** Necessary condition for a single iteration of the subtree to be achievable within the
     * {@code hi} bounds (used by + and by * with a non-zero slice). Occupancy level only. */
    private boolean once(TripleExpr expr) {
        if (expr instanceof TripleConstraint tc)
            return hi[idx(tc)] >= 1;
        if (expr instanceof TripleExprEmpty)
            return true;
        if (expr instanceof EachOf eachOf) {
            for (TripleExpr sub : eachOf.getTripleExprs())
                if (! once(sub))
                    return false;
            return true;
        }
        if (expr instanceof OneOf oneOf) {
            for (TripleExpr branch : oneOf.getTripleExprs())
                if (once(branch))
                    return true;
            return false;
        }
        if (expr instanceof TripleExprCardinality card) {
            TripleExpr sub = card.getSubExpr();
            int min = card.min();
            if (sub instanceof TripleConstraint tc)
                return min == 0 || hi[idx(tc)] >= min;
            if (min == 0)
                return true;
            return once(sub);                                   // +
        }
        throw new IllegalArgumentException("Unexpected expression in SORBE form: " + expr.getClass());
    }

    /** Whether the slice of the subtree can be completed to all-zero, ie no triple is committed
     * to any of its constraints yet. */
    private boolean zeroPossible(TripleExpr expr) {
        for (int tcIdx : subtreeTcIndexes.get(expr))
            if (lo[tcIdx] != 0)
                return false;
        return true;
    }

    /** [lo,hi] of the constraint intersects [min,max]. */
    private boolean boundsAllow(TripleConstraint tc, int min, int max) {
        int i = idx(tc);
        return lo[i] <= max && hi[i] >= min;
    }

    private int idx(TripleConstraint tc) {
        Integer i = tcIndex.get(tc);
        if (i == null)
            throw new IllegalArgumentException("Unknown triple constraint");
        return i;
    }
}
