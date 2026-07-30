# Feasibility-pruned matching search for ShEx validation

*Status: implemented on branch `extends-validation-error-reporting`; all 2142 tests pass
(2135 shexTest/extends conformance + 7 dedicated tests).*

This document specifies and justifies the data structure and search algorithm used by
jena-shex to allocate the triples of a node's neighbourhood to the triple constraints of a
shape's triple expression — including the triple expressions inherited through `EXTENDS` —
and argues that the algorithm is **sound**, **complete**, and **optimal within stated
parameters**. It is written to be implementation-independent: the concepts port to any ShEx
validator that follows the partition-based semantics of
[shex-next §Triple expression semantics](https://shex.io/shex-next/#triple-expressions-semantics).
Jena file references are given so a reader can follow along in code.

Contents:

1. [The problem](#1-the-problem)
2. [Preliminaries: SORBE, bags, the interval verifier](#2-preliminaries)
3. [Semantic ground rules pinned by the test suite](#3-semantic-ground-rules)
4. [Why search is unavoidable (hardness), and what "optimal" can mean](#4-hardness)
5. [The data structure: triple classes and partial bags](#5-data-structure)
6. [The feasibility predicate F](#6-feasibility-predicate)
7. [Soundness of F (theorem and proof)](#7-soundness)
8. [The search algorithm](#8-algorithm)
9. [Completeness of the algorithm (theorem)](#9-completeness)
10. [Optimality within parameters](#10-optimality)
11. [Screw cases](#11-screw-cases)
12. [EXTENDS: inheritance as conjunction](#12-extends)
13. [Bugs found and fixed along the way](#13-bugs)
14. [Porting guide](#14-porting)
15. [Further work](#15-further-work)

---

## 1. The problem

To decide `satisfies(n, S)` for a node `n` and a shape `S` with triple expression `E`,
partition-based ShEx semantics asks whether *some* assignment ("matching") of the relevant
neighbourhood triples to the triple constraints (TCs) of `E` satisfies `E`'s structure
(cardinalities, `EachOf` grouping, `OneOf` alternation). With `EXTENDS`, the neighbourhood
must additionally *split* among the shapes of the extension hierarchy such that every
supertype's triple expression accepts its share, and each supertype's remaining constraints
accept the split parts.

The naive algorithm — used by jena-shex until this work — is:

1. **Pre-matching**: for each triple `t`, compute the candidate set `cands(t)` = TCs with the
   same predicate whose value expression accepts `t`'s object (recursively validated). This is
   `predicateBasedPreMatching` + `filterRecursiveValidation` in
   `validation/TripleExprEval.java`.
2. **Enumeration**: iterate the full Cartesian product `Π_t cands(t)`
   (`validation/MatchingsIterator.java`).
3. **Verification**: for each matching, build the *bag* (multiset of TCs) and run the exact
   interval computation (`validation/IntervalComputation.java`); with `EXTENDS`, run it for
   every triple expression in the hierarchy.

Step 2 is exponential in the number of triples whose candidate sets have more than one
element, i.e. whenever TCs share a predicate — which is exactly the interesting case (across
`OneOf` branches, across supertypes, or between a shape and its `EXTRA`-tolerated context).
Measured on the schema `{ :p . {1,2} | :p . + ; :q . }` with *n* `:p`-triples and no
`:q`-triple (nonconformant): 2^n full verifications — 65 536 checks / 166 ms at n = 16,
16.7 M checks at n = 24, unusable in the range where clinical FHIR profiles operate.

The replacement (`validation/FeasibleMatchingsIterator.java` +
`validation/TripleExprFeasibility.java`) restructures step 2 around two ideas:

* **Enumerate bags, not matchings.** Validity of the triple expression depends only on the
  bag (counts per TC): the exact verifier consumes a `Bag` and discards which triple went
  where. Triples are of course all distinct (they come from a set of (S, P, O)), so
  interchangeability is *established*, not assumed: the `O(n·k)` upfront value evaluations
  of step 1 fix, for every (triple, TC) pair, whether the object satisfies the TC — and
  once that boolean matrix is fixed, two triples with identical candidate *sets* are
  indistinguishable to a bag-consuming verifier. Group them into *classes* and enumerate
  count *distributions*: a class of `n` triples over `k` candidates has `C(n+k−1, k−1)`
  distributions — `O(n^(k−1))`, polynomial in `n` for fixed `k` — instead of `k^n`
  assignments (n = 16, k = 2: 17 instead of 65 536). The collapse is only dramatic where
  classes are fat, i.e. where triples share candidate sets; adversarial data in which every
  triple has a distinct candidate set yields singleton classes and degenerates to the
  original bound — which is why the feasibility predicate below is an independent lever,
  not an optimization of this one.
* **Refute early.** A compositional *feasibility predicate* `F` over partially-determined
  bags refutes assignments that cannot be completed to an accepted bag — capturing minimum
  cardinalities, `EachOf` co-occurrence obligations, `OneOf` exclusivity, and maximum
  cardinalities — and is applied (a) upfront as an arc-consistency pass that deletes
  candidate TCs (the generalization of "eliminate a TC from `t → [TC]` when a test fails"
  from value checking to structural infeasibility), and (b) at every node of the
  distribution search.

The same measurement after the change: **0** verifications at every n ≥ 3, ~1 ms at n = 24;
conformance answers unchanged on the entire suite.

## 2. Preliminaries

### 2.1 SORBE form

jena-shex compiles every triple expression to **SORBE** form (Single Occurrence Regular Bag
Expression; `TripleExprForValidation.SorbeConstructor`): triple expression references are
inlined, and cardinalities other than `?`, `*`, `+`, `{0,0}` may appear only directly on
TCs (a `(E){2,4}` becomes `E ; E ; E? ; E?` with fresh copies of the TCs). Consequences used
throughout:

* **Single occurrence**: every TC object occurs exactly once in the tree; sibling subtrees
  have **disjoint TC alphabets**. (Copies produced by the rewrite are distinct objects; all
  maps over TCs must be identity-based, not equality-based — SORBE copies are `equals`.)
* `+` appears only on subexpressions that cannot match the empty bag ("non-nullable"); a `+`
  over a nullable body is rewritten to `*`.
* The grammar of the tree is: `TC[m,n]` (bare `TC` ≡ `TC[1,1]`), `Empty`, `EachOf(E…)`,
  `OneOf(E…)`, `E?`, `E*`, `E+`, `E{0,0}`.

### 2.2 Bag languages

The set of bags accepted by a SORBE expression, `L(E)`, over the (disjoint) coordinates of
its TCs:

| E            | L(E)                                                        |
|--------------|-------------------------------------------------------------|
| `TC[m,n]`    | `{ k·TC : m ≤ k ≤ n }`                                       |
| `Empty`      | `{ 0 }`                                                      |
| `EachOf(E₁…Eₖ)` | `L(E₁) + … + L(Eₖ)` (Minkowski sum; = product, by disjointness) |
| `OneOf(E₁…Eₖ)`  | `⋃ᵢ L(Eᵢ)` (other branches' coordinates are 0)            |
| `E?`         | `L(E) ∪ {0}`                                                 |
| `E*`         | `⋃_{q≥0}` (sum of `q` members of `L(E)`)                     |
| `E+`         | `⋃_{q≥1}` (sum of `q` members of `L(E)`)                     |

Two consequences that any optimization must respect (§3 pins them with suite tests):

* **`OneOf` exclusivity does not survive repetition**: `L((a|b)+)` contains mixed bags
  (`{a:1, b:1}`), because each iteration chooses independently; `L(a|b)` does not.
* **Repetition couples counts** (Parikh-image constraints): `L((a;b)+) = {(k,k) : k ≥ 1}`,
  and `L((a{2,2})*)` = even counts only. These are *not* expressible as independent
  per-TC intervals.

### 2.3 The exact verifier

For a *complete* bag `w`, `IntervalComputation` computes, bottom-up, the interval of
"repetition numbers" `I(E, w) = { k : w ∈ L(E^k) }`, using: `I(TC, w) = [w(TC), w(TC)]`;
`EachOf` = interval intersection; `OneOf` = Minkowski sum; `TC[m,n]` = "division"
`[⌈w(TC)/n⌉, ⌊w(TC)/m⌋]`; `*`/`+` by case analysis on emptiness of the sub-bag. Then
`w ∈ L(E)` iff `1 ∈ I(E, w)`. This is the Boneva–Staworko-style interval algorithm; it is
exact for SORBE and linear in `|E|`. It remains the **final arbiter** for every complete bag
in this design. (Two latent defects in the Jena implementation of this verifier were found
and fixed during this work — see §13.)

### 2.4 EXTENDS in one sentence

`satisfiesShape` (`validation/ShapeExprEval.java`) collects the *main* triple expressions of
the shape and of all its extended supertypes, builds one joint pre-matching over the union
of their TCs, and requires a single matching that satisfies **every** expression at once;
the matching then induces the *split* (which triples went to which supertype's TCs) against
which each supertype's remaining conjuncts ("constraints", e.g. from `RESTRICTS`-style
`ShapeAnd`s) are validated. Because the TC alphabets of the hierarchy's expressions are
disjoint, *the conjunction is exactly one more `EachOf` level over the joint alphabet* —
so everything below applies to `EXTENDS` with no special machinery (§12).

## 3. Semantic ground rules pinned by the test suite

Any search-space optimization must preserve these facts, each pinned by an executable test
(`validation/MatchingSearchProbesTest.java`, plus approved shexTest entries):

1. **Forced assignment.** A triple whose predicate is mentioned in the expression and whose
   value matches at least one TC can **never** be left unmatched — not even when its
   predicate is in `EXTRA`. Approved shexTest case `1val2IRIREFExtra1_fail-iri2`
   (`EXTRA <p1> { <p1> [<o1> <o2>] }` on `{<s1> <p1> <o1>, <o2>}` → *failure*) pins this.
   `EXTRA` only excuses unmatched triples that match *no* TC. Consequence: the candidate
   data model must **not** offer a "drop this triple" alternative; if pruning empties a
   triple's candidate set, the shape is unsatisfiable (probe 5, probe 2).
2. **`OneOf` exclusivity is per-match, not global.** `{ :a . | :b . }` rejects `{a,b}`;
   `{ (:a . | :b .)+ }` accepts it (probe 3). A DNF-style compilation that treats `OneOf`
   as a global exclusive-or is unsound under repetition.
3. **Repetition couples counts.** `{ (:a . ; :b .)+ }` rejects 2×`a` + 1×`b` (probe 4).
   Occupancy reasoning alone cannot decide validity; a final exact check is mandatory.
4. **The interval verifier is only correct if empty intervals propagate.** See §13.2.

## 4. Why search is unavoidable, and what "optimal" can mean

Deciding whether *some* assignment of triples to candidate TCs yields an accepted bag is a
constraint-satisfaction problem. For general (non-single-occurrence) RBEs it is NP-complete
(Boneva, Labra Gayo, Prud'hommeaux, Staworko et al., *Complexity and Expressiveness of ShEx
for RDF*, ICDT 2015); for single-occurrence expressions a polynomial decision procedure via
flow networks exists (same line of work), **but** a practical validator needs more than the
decision bit: it must *stream the witnesses* (matchings), because downstream checks consume
them — `EXTENDS` constraints are evaluated against the concrete split, and semantic actions
against the concrete triples matched to each subexpression. Two matchings with the same bag
can differ on those checks. Witness enumeration is exponential in the worst case simply
because the answer can be exponential.

Therefore the honest optimality target is not polynomial time but:

* (**P1**) never materialize the per-triple assignment space when the bag space suffices;
* (**P2**) never *explore* a state refutable by sound local reasoning over per-TC count
  intervals and occupancy implications (the class of reasoning formalized by `F`, §6);
* (**P3**) spend at most linear time (in `|E|`) per explored state;
* (**P4**) remain exact — identical accepted-matching sets as the naive algorithm.

§10 states precisely which of these the implementation achieves.

## 5. The data structure: triple classes and partial bags

**Triple classes.** Group the triples by candidate set: `class(C) = { t : cands(t) = C }`.
Classes are the unit of search. Assigning a class means choosing a *composition*: a count
vector over its candidate TCs summing to the class size. (Which *concrete* triples take
which candidate is deferred to the expansion phase, §8.4.)

Why classing is sound: `cands(t)` is computed by the pre-matching (predicate match *plus*
recursive validation of `t`'s object against each TC's value expression), so membership in
the same class certifies that the same TCs accepted both objects; and the exact verifier is
a function of the bag alone, so swapping two same-class triples in a matching cannot change
the triple expression's verdict. This rests on value evaluation being context-free — a
value expression is satisfied by the object against its own neighbourhood, independently of
the partition being tried — which is the same premise the Cartesian enumerator already
relies on when it memoizes the per-(triple, TC) hits and misses. It does **not** extend to
consumers of concrete triples (EXTENDS constraints on splits, semantic actions): for those,
same-bag matchings genuinely differ, which is what the expansion phase is for.

**Partial bags.** During search, each TC `tc` carries an interval:

* `lo(tc)` — triples already committed to `tc` by assigned classes;
* `hi(tc) = lo(tc) + potential(tc)`, where `potential(tc)` = total size of not-yet-assigned
  classes having `tc` as a candidate.

A **completion** is any exact bag `c` with `lo ≤ c ≤ hi` pointwise. The completions form a
superset of the bags actually reachable by the remaining search (they ignore the "each class
distributes exactly its size" coupling); refuting *all* completions therefore refutes all
reachable bags — this over-approximation is what makes refutation sound and cheap.

**Static maxima.** For each TC, precompute `staticMax(tc)`: its declared max if no ancestor
is a repetition, else unbounded. Used to cap compositions before `F` is even consulted.

## 6. The feasibility predicate F

`F(lo, hi)` (implemented in `TripleExprFeasibility`) returns `false` only if **no**
completion is accepted by every expression of the hierarchy. It is computed compositionally
over each SORBE tree in one of two modes, plus two auxiliaries. `zero(E)` means "the slice
of `E` can be completed to all-zero", i.e. `∀ tc ∈ E: lo(tc) = 0`.

**Exact mode** `Fx(E)` — necessary condition for some completion slice to be accepted
exactly once by `E`:

| E | Fx(E) |
|---|-------|
| `TC[m,n]` | `lo(TC) ≤ n ∧ hi(TC) ≥ m` |
| `Empty` | true |
| `EachOf(E₁…Eₖ)` | `⋀ᵢ Fx(Eᵢ)` |
| `OneOf(E₁…Eₖ)` | `⋁ᵢ ( Fx(Eᵢ) ∧ ⋀_{j≠i} zero(Eⱼ) )` |
| `E{0,0}` | `zero(E)` |
| `E?` | `zero(E) ∨ Fx(E)` |
| `E*` | `zero(E) ∨ ( Fi(E) ∧ once(E) )` |
| `E+` | `Fi(E) ∧ once(E)` |

**Iterated mode** `Fi(E)` — necessary condition for some completion slice to be a sum of
`q ≥ 1` bags each in `L(E)`. This is the *monotone weakening* of `Fx`: exclusivity and upper
bounds do not survive summation, but `EachOf` co-occurrence survives at the occupancy level:

| E | Fi(E) |
|---|-------|
| `TC` (bare) | `hi(TC) ≥ 1` |
| `TC[m,n]` | `( m = 0 ∨ hi(TC) ≥ m ) ∧ ( n > 0 ∨ lo(TC) = 0 )` |
| `Empty` | true |
| `EachOf(E₁…Eₖ)` | `⋀ᵢ Fi(Eᵢ)` |
| `OneOf(E₁…Eₖ)` | `⋀ᵢ ( zero(Eᵢ) ∨ Fi(Eᵢ) )`  — *mixing allowed* |
| `E{0,0}` | `zero(E)` |
| `E?`, `E*` | `zero(E) ∨ Fi(E)` |
| `E+` | `Fi(E) ∧ once(E)` |

**Occupancy of one iteration** `once(E)` — necessary condition for a single non-empty
iteration to fit within `hi` (needed by `+`, and by `*` when the slice cannot be zero):

| E | once(E) |
|---|---------|
| `TC[m,n]` | `m = 0 ∨ hi(TC) ≥ m` |
| `EachOf` | `⋀ᵢ once(Eᵢ)` |
| `OneOf` | `⋁ᵢ once(Eᵢ)` |
| `E?`,`E*`,`E{0,0}` | true |
| `E+` | `once(E)` |

For the hierarchy: `F = ⋀_root Fx(root)` — by alphabet-disjointness this is exactly the
`EachOf` rule applied at a virtual root (§12).

**What F deliberately ignores** (and why the exact verifier stays): count coupling inside
repeated groups (`(a;b)+` with unequal counts passes `Fi`) and divisibility
(`(a{2,2})*` with an odd count passes). These are Parikh constraints not expressible as
independent intervals + occupancy implications; capturing them exactly would re-introduce
the complexity `F` exists to avoid. Complete bags passing `F` are always re-checked by
`IntervalComputation` downstream, so this loses no correctness — only some pruning power
(§10, P2 scope).

## 7. Soundness of F

**Theorem 1 (refutation soundness).** If some completion `c` (`lo ≤ c ≤ hi`) satisfies
`c|_E ∈ L(E)`, then `Fx(E)` holds; if some completion satisfies `c|_E ∈ Σ_{i≤q} L(E)` for
some `q ≥ 1`, then `Fi(E)` holds. Hence `F(lo,hi) = false` implies no completion is accepted
by the hierarchy.

*Proof* (structural induction on `E`; fix the witnessing completion `c`).

* `TC[m,n]`, exact: `c(TC) ∈ [m,n]` and `lo ≤ c(TC) ≤ hi`, so `lo ≤ n` and `hi ≥ m`. ∎
* `Empty`: trivial.
* `EachOf`, both modes: sibling alphabets are disjoint (single occurrence), so acceptance of
  the slice decomposes componentwise: `c|_{Eᵢ} ∈ L(Eᵢ)` (exact) resp. `c|_{Eᵢ}` is a sum of
  `q` members of `L(Eᵢ)` — note the *same* `q` works for each component, and `Fi` never uses
  `q`, so the inductive hypothesis applies to each child. ∎
* `OneOf`, exact: acceptance selects a branch `i` with `c|_{Eᵢ} ∈ L(Eᵢ)` and `c|_{Eⱼ} = 0`
  for `j ≠ i`; then `lo(tc) ≤ c(tc) = 0` on every `tc ∈ Eⱼ`, i.e. `zero(Eⱼ)`, and `Fx(Eᵢ)`
  by induction. ∎
* `OneOf`, iterated: each of the `q` iteration bags selects one branch; for each branch `j`,
  either no iteration selects it — then `c|_{Eⱼ} = 0` and `zero(Eⱼ)` holds — or the
  iterations selecting it sum to `c|_{Eⱼ}`, a sum of `≥ 1` members of `L(Eⱼ)`, so `Fi(Eⱼ)`
  by induction. ∎
* `E?` exact: `c|_E` is `0` (then `zero(E)`) or in `L(E)` (then `Fx(E)`). ∎
* `E*` exact: `c|_E` is `0`, or a sum of `q ≥ 1` members of `L(E)`; discard empty summands —
  possible w.l.o.g. since we may take `q` minimal — leaving `q' ≥ 1` non-empty summands, so
  `Fi(E)` holds; moreover any single summand `w ≤ c|_E ≤ hi` witnesses `once(E)` (each
  `TC[m,n]` with `m ≥ 1` occurring non-optionally in the chosen branch structure of `w` has
  `w(TC) ≥ m`, and `once` only asserts `hi ≥ m` along one `OneOf` branch, satisfied by the
  branch `w` uses). ∎
* `E+`: as `E*` with `q ≥ 1` guaranteed; SORBE guarantees `E` non-nullable so summands are
  non-empty. ∎
* `TC[m,n]` iterated: `c(TC) = Σ_{i≤q} kᵢ` with each `kᵢ ∈ [m,n]`, `q ≥ 1`. If `m ≥ 1`,
  `c(TC) ≥ m`, so `hi ≥ m`. If `n = 0`, every `kᵢ = 0`, so `c(TC) = 0 ≥ lo`. ∎
* `E{0,0}`: the slice is `0`; as in the `zero` cases. ∎

**Corollary.** The arc-consistency deletion (remove candidate `tc` from class `C` when
`F(lo = 1_{tc}, hi) = false`) deletes only pairs `(t, tc)` that appear in **no** accepted
matching: an accepted matching assigning `t → tc` induces a completion with `c(tc) ≥ 1`.
Likewise every partial-distribution refutation during search only cuts subtrees containing
no accepted bag. ∎

## 8. The search algorithm

(`FeasibleMatchingsIterator`; drop-in replacement for the Cartesian `MatchingsIterator`.)

```
build classes; potential(tc) := Σ |class| over classes with tc ∈ cands
1. Arc consistency (fixpoint):
     for each class C, candidate tc:
         if ¬F(lo = 1_tc, hi = potential) → remove tc from C; potential(tc) -= |C|
     if some class loses all candidates → emit nothing (unsatisfiable, §3 rule 1)
2. Distribution search (DFS over classes, fewest-candidates-first):
     at each level, enumerate compositions of |C| over cands(C),
         each count capped by min(|C|, staticMax(tc) − lo(tc))   [also avoids overflow]
     after committing a composition: if ¬F(lo, lo + potential) → try next composition
     all classes assigned → a complete candidate bag
3. Expansion of each surviving bag:
     per class, enumerate the distinct assignments of its concrete triples realizing the
     composition (multiset permutations); Cartesian product across classes, lazily.
4. Downstream (unchanged): exact interval verification per matching (per hierarchy
     expression), mandatory-TC filter, split construction, EXTENDS-constraint checks,
     semantic actions.
```

Reporting note: error reporting deliberately probes the *unpruned* iterator
(`TripleExprEval.correctSplitsIterator`), so reports are bit-identical to the historical
ones; pruning affects only the validation stream.

## 9. Completeness of the algorithm

**Theorem 2 (exactness).** The set of matchings accepted by the validator (i.e. surviving
the exact per-expression interval checks) is identical with the feasible iterator and with
the Cartesian iterator.

*Proof.* (⊆) Every matching emitted by the feasible iterator assigns each triple one of its
original candidates, hence is emitted by the Cartesian iterator; downstream checks are
unchanged. (⊇) Let `M` be a matching accepted by the exact checks and `w` its bag. By
Theorem 1's corollary, no candidate used by `M` is deleted by arc consistency; at every
prefix of the class ordering, the partial distribution induced by `M` has the completion `w`
accepted by the hierarchy, so `F` holds and the DFS does not prune the branch containing it;
the composition caps hold because `w(tc) ≤ staticMax(tc)` for every accepted bag (a count
above the static maximum makes the exact interval empty); hence `M`'s bag is reached, and
the expansion phase enumerates *all* assignments realizing the bag, including `M`. ∎

This is verified mechanically by `TestFeasibleMatchingSearch`: for eight adversarial
expression patterns and two 2-expression conjunctions (the `EXTENDS` shape), across all data
sizes `nP ≤ 4, nQ ≤ 2/3`, the set of valid matchings from both iterators is asserted equal,
and the feasible stream is asserted to be a subset of the Cartesian stream.

## 10. Optimality within parameters

What is achieved, in the terms of §4:

* **P1 (bag granularity)** — achieved. Per-triple choices are never enumerated until a
  complete bag has survived `F`; a class of `n` triples with `k` candidates contributes
  `C(n+k−1, k−1)` search nodes — `O(n^(k−1))`, polynomial in `n` for fixed `k` — not `k^n`.
  Stated precisely: the `n·k` upfront value evaluations convert the per-class assignment
  space from `k^n` to `C(n+k−1, k−1)` *for the decision problem*, deferring witness
  enumeration to surviving bags. The bound helps exactly where candidate sets repeat;
  all-singleton classes reproduce the Cartesian bound, and there the pruning burden falls
  entirely on `F`. Expansion cost is incurred only on bags that can still win, and is
  itself output-bounded (each expansion step emits a matching).
* **P2 (no F-refutable exploration)** — achieved by construction *at the granularity of
  class-level commitments*: every explored node has passed `F` on its own state. Singleton
  arc consistency additionally guarantees that every surviving `(triple, TC)` pair is
  F-supported before search starts. Not claimed: refutations that require reasoning about
  *combinations* of future classes (F's completion set is a relaxation), count coupling, or
  divisibility (§6). This boundary is principled: `F` is decidable in `O(|E|)` per state
  (P3), while closing the gap requires Parikh/flow reasoning (§15).
* **P3 (linear-time states)** — achieved: one `F` evaluation walks each SORBE tree once;
  `zero(E)` uses precomputed subtree TC-index arrays.
* **P4 (exactness)** — Theorem 2.

Measured effect (probe 1, `{ :p . {1,2} | :p . + ; :q . }`, nonconformant instances):

| n (triples) | matchings verified before | after | wall time after |
|------------:|--------------------------:|------:|----------------:|
| 16 | 65 536 (166 ms) | 0 | < 1 ms |
| 24 | 16 777 216 (extrapolated ~40 s) | 0 | 1 ms |

The full 2135-test conformance suite runs in the same ~1.9 s as before the change: the
machinery adds no measurable constant overhead in the common (unambiguous) case, because
classes with a single candidate produce exactly one composition and `F` runs once.

## 11. Screw cases

Worked examples a correct implementation must handle; all are executable tests.

**11.1 Shared predicate across `OneOf` branches** (probe 1). `{ :p . {1,2} | :p . + ; :q . }`
with `n ≥ 3` `:p`-triples, no `:q`. Arc consistency tests the second branch's `:p`-TC with
`lo = 1`: exact-mode `OneOf` demands either branch 2 feasible — but its `EachOf` sibling
`:q .` has `hi = 0 < 1` — or branch 1 feasible with branch 2's slice zero — but `lo = 1`.
Refuted; the TC is deleted *for all triples at once*. The lone remaining distribution
(everything on the `{1,2}`-TC) fails `Fx` at `lo = n > 2`. Zero enumeration. Note the
per-triple "does the value match" test could never discover this: every triple matches both
TCs individually; infeasibility is a property of the *ensemble*.

**11.2 The distinct-predicate trap** (probe 2). The motivating example
`{ :name .+ | :givenName .+ ; :familyName . }` with `givenName` data and no `familyName`
never suffered combinatorial blowup — every triple has exactly one candidate — so no
enumeration-order cleverness helps it. What the feasibility layer adds is *refutation before
verification*, and more importantly the machinery that pays off when predicates *are*
shared. Optimization work targeted at this example alone would have measured nothing.

**11.3 Exclusivity under repetition** (probe 3, §3 rule 2). Any normal-form compilation
(CNF/DNF over "TC occupied" literals) must use the *monotone weakening* under `*`/`+`:
compile `OneOf` to exclusive choice only outside repetition contexts. Getting this wrong
rejects `{ (:a . | :b .)+ }` on `{a, b}` — which is conformant.

**11.4 Count coupling** (probe 4, §3 rule 3). `(a;b)+` with counts (2,1) passes every
occupancy test and must be killed by the exact verifier. Deleting the final exact check
because "F already checked" is unsound.

**11.5 `EXTRA` does not mean droppable** (probe 5, §3 rule 1). A candidate-model with an
"unassigned" pseudo-TC for `EXTRA` predicates accepts `1val2IRIREFExtra1_fail-iri2` — an
approved failure. Forced assignment is the spec.

**11.6 Unbounded maxima overflow** (found by suite test `nPlus1`). Caps for composition
enumeration must clamp `staticMax − lo` at the class size *before* summing capacities;
summing `Integer.MAX_VALUE` capacities overflows and silently truncates the enumeration —
a completeness bug that surfaced as a single failing suite test out of 2135.

**11.7 Empty-interval leakage in the verifier** (§13.2) — found by the differential test.

## 12. EXTENDS: inheritance as conjunction

The joint matching for an extension hierarchy `{S, P₁, …, Pₖ}` must satisfy every member's
main triple expression on its own TC alphabet. Since the alphabets are disjoint, the
hierarchy is semantically the single expression `EachOf(E_S, E_{P₁}, …, E_{Pₖ})`, and:

* the feasibility of the hierarchy is `⋀ᵢ Fx(Eᵢ)` — the `EachOf` rule at a virtual root;
* pruning propagates *across* the hierarchy: a triple whose only candidates are in a parent
  whose co-occurrence obligations the data cannot meet is refuted once, globally
  (this is what makes `EXTENDS` validation of e.g. FHIR vitals profiles tractable);
* the *split* (triples per supertype) is a projection of the matching; supertype constraints
  (`ShapeAnd` conjuncts beyond the main shape) are evaluated per split part — this is why
  the algorithm must stream *matchings*, not just decide bag existence.

**Current restriction and the path to relaxing it.** jena-shex requires every extended
shape expression to expose a *main shape* — first conjunct of a `ShapeAnd` (after
dereference), with all constraint predicates contained in the hierarchy's main-shape
predicates (`calc/SchemaAnalysis.checkExtendsCorrect`). A parent that is a `ShapeOr` of
shapes is rejected. The postulate that the `OneOf` machinery extends to `ShapeOr` parents is
*correct with one distinction*: a `OneOf` choice is resolved per iteration and partitions a
bag among branches, while a `ShapeOr` choice is resolved once per (node, shape) evaluation
and selects which TC alphabet participates at all. Concretely, a relaxation would:

1. build a *selection tree* (AND/OR over shape atoms) instead of the flat supertype list;
2. for each selection σ (choice of one branch per reachable `ShapeOr`), the participating
   TEs form the virtual `EachOf` as today; `F` gains a top-level disjunction over σ —
   which can reuse exactly the exact-mode `OneOf` rule, with `zero(E_branch)` expressing
   "no triple committed to the unselected branch's TCs";
3. `matchables`/`CLOSED`/`EXTRA` become selection-dependent: a triple whose predicate is
   mentioned only in an unselected branch is a *non-matchable* for that selection. The
   unmatched-triples test must therefore move inside the per-selection loop;
4. `ShapeNot` (at any depth) never contributes candidate TCs — a negated shape is evaluated
   as a filter over an already-determined triple set, never as a sink for assignments. Its
   predicates' contribution to `matchables` is a semantic decision the shex-next spec must
   settle (today the main-shape predicate-inclusion restriction makes the question moot);
   the implementation should keep negated TCs out of the pre-matching either way.

Point 2 is the load-bearing one: no new data structure is needed — the selection layer *is*
an exact-mode `OneOf` over shape atoms.

## 13. Bugs found and fixed along the way

Recorded because both are cautionary tales for other implementations.

**13.1 `Map.putIfAbsent` vs. split-restricted constraints** (`ShapeExprEval.
splitSatisfiesConstraints`). `putIfAbsent` returns **null on first insertion**, so the first
constraint of each supertype was validated with a `null` triple set — the
whole-neighbourhood path — instead of the split part. Effect: all 17 `Extend*`/
`vitals-RESTRICTS-pass_*` tests failed (validation too strict). Fix: `computeIfAbsent`.
Porting lesson: constraints of an extended shape are evaluated against the *union of the
split parts of its supertypes*, and an accidental fallback to the full neighbourhood is
easy to miss because it only bites when the split is a proper subset.

**13.2 Empty intervals must propagate through the `OneOf` sum**
(`IntervalComputation.add`). The interval "division" for `TC[m,n]` yields empty intervals in
non-canonical forms (e.g. `[1,0]` for count 1 against `{2,4}`); the `OneOf` rule
(Minkowski sum) then computed `[0,∞] + [1,0] = [1,∞] ∋ 1`, wrongly accepting e.g.
`{ :p . {0,0} | :p . {2,4} }` with a single `:p`-triple. A sum with an empty operand must be
empty, and emptiness tests must be `min > max`, not identity with a canonical `[2,1]`.
Found on the first run of the differential harness — the feasibility predicate refuted a
matching the "exact" verifier accepted, and the disagreement was the bug's fingerprint.
Porting lesson: if your interval algebra has a distinguished empty element, audit *every*
operation for producing or consuming non-canonical empties; better, define `isEmpty`
structurally.

## 14. Porting guide

To reproduce this design in another partition-based validator:

1. **Prerequisite**: a SORBE (or equivalently normalized) tree per triple expression, an
   exact bag-membership verifier, and per-triple candidate sets already filtered by
   predicate + recursive value satisfaction. Identity-distinguish TC copies.
2. Implement the three tables of §6 verbatim; they only need: per-subtree TC lists,
   nullability, `lo`/`hi` arrays. (~250 lines in Java.)
3. Replace the assignment enumerator with: class grouping → AC fixpoint → composition DFS
   with `F` at each commit → per-bag multiset-permutation expansion. (~300 lines.)
   Keep your existing verification of every emitted matching. Clamp composition caps
   (§11.6).
4. Do **not** change: forced assignment (§3.1), the final exact check (§3.3), closed/extra
   handling of zero-candidate triples (they are removed *before* the iterator and tested
   against `EXTRA`; triples whose candidates are emptied *by pruning* are not extra —
   they make the shape fail).
5. Validate with (a) your conformance suite, (b) a differential harness asserting
   valid-matching-set equality between the pruned and Cartesian enumerators on exhaustive
   small instances of the §11 patterns — it is the only test in this work that caught bugs
   the 2135-case conformance suite missed (both §13.2 and an early state-machine defect).

## 15. Further work

* **Bag-level exact check before expansion**: verify each surviving bag once with the
  interval verifier and skip expansion of invalid bags (currently every expanded matching is
  verified individually — redundant within a bag). Kept out for now to leave the reporting
  stream unchanged.
* **Parikh-aware pruning**: capture equal-count coupling of repeated `EachOf` groups with
  difference constraints (`|count(a) − count(b)| = 0` per iteration group), closing most of
  the residual gap in P2 at modest cost.
* **Flow-based decision procedure**: for validate-only calls with no semantic actions and no
  `EXTENDS` constraints, the single-occurrence flow construction decides conformance in
  polynomial time without enumerating witnesses; the feasibility layer would remain for the
  witness-streaming paths.
* **`ShapeOr` in EXTENDS**: §12's selection-tree relaxation, lifting the main-shape
  restriction (`TODO this requirement is not included in ESWC` in `SchemaAnalysis`).
* **Memoized `F` / incremental `zero`**: `F` is re-evaluated from scratch per state; the DFS
  changes few `lo` entries between evaluations, inviting incremental recomputation along
  the tree spine of the touched TCs.
