package org.apache.jena.shex.reporting;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.validation.TripleExprForValidation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Receives information about events regarding validation and uses them to construct a validation report.
 * Every reporter is dedicated to a pair (node, expr) of a node and a (shape or triple) expression, and has a hierarchic structure.
 * Root reporters (see {@link #createRoot(Node, ShapeExprRef)}) correspond to a pair where the expression is a shape expression reference, as required to be validated by a shape map.
 * The children of a reporter for (node, expr) are about pairs (node', expr') required to be validated while validating node againts expr.
 * That is, expr' is typically a sub-expression of expr, or a reference that appears in expr, or the definition of a reference that appears in expr, and node' is either node itself, or a neighbour of node in the graph.
 * */
public interface Reporter {

    /** The validation report produced by this reporter. */
    Report getReport();

    /** A root reporter used for validating the given node against the given shape expression reference as required by a shape map. */
    Reporter createRoot(Node node, ShapeExprRef expr);
    /** A child reporter for this reporter. */
    Reporter createChild(Node node, Expression expr, Set<Triple> neigh);
    /** Indicates that this reporter's result is already known and is the report given as parameter.
     * Used when the result of validating this reporter's node against this reporter's expression is already known (due to recursion, or due to a previous validation).
     */
    void setReferenceTo (Report report, String additionalMessage);

    /** Informs the reporter about the status of validation.
     * No other interaction with the reporter should be done after calling this method, except for {@link #getReport()} */
    void setResult(ShexStatus status);

    /** A generic method for informing the reporter about events regarding validation. Should not be used directly, but through the other more specific inform methods.*/
    void addInfo(boolean isConformant, String message, Object details);

    /** Indicates whether the reporter is only interested in the result of the validation, but not in detailed reports.
     * Used to speed up validation whenever the reporter is validate-only. */
    boolean isValidateOnly();

    /** Sets the result to conformant or non-conformant.
     * Returns the value of its parameter. */
    default boolean setIsConformant(boolean isConformant) {
        setResult(isConformant ? ShexStatus.conformant : ShexStatus.nonconformant);
        return isConformant;
    }
    
    /** Informs whether this reporter's shape expression is satisfied directly, or through one of its non-abstract descendants. */
    default void informDescendantConformance(boolean isConformant, Node satisfiedDescendantIfConformant) {
        if (isConformant)
            addInfo(true, "The non-abstract descedant " + satisfiedDescendantIfConformant + " is satisfied", null);
        else
            addInfo(false, "No non-abstract descendant is satisfied.", null);

    }

    /** Informs whether the semantic actions are satisfied. */
    default void informSemanticActionsConformance(boolean isConformant, SemAct semAct) {
        String m = isConformant ? "Semantic actions satisfied." : "Semantic actions not satisfied.";
        addInfo(isConformant, m, semAct);
    }

    /** Informs that a component of a node constraint is not satisfied. Used when validating against a node constraint. */
    default void informInvalidNodeConstraintComponent(NodeConstraintComponent c, String message) {
        addInfo(false, message, c);
    }

    /** Informs that external shape definitions are not supported. */
    default void informExternalNotSupported() {
        addInfo(false, "Shape external not supported.", null);
    }

    /** Informs that the expression is satisfied because of a cycle on the validation stack. */
    default void addInfoCycle() {
        addInfo(true, "Cycle.", null);
    }

    /** Informs about unexpected triples because of closed shape or missing extra. */
    default void informUnexpectedTriples(Set<Triple> triples, UnexpectedTriplesReason reason) {
        String m = switch(reason) {
            case CLOSED -> "Closed shape";
            case EXTRA -> "The triples didn't match any of the triple constraints.";
        };
        addInfo(false, "Unexpected triples. " + m, triples);
    }

    default void informCandidateMatchingConformance(boolean isConformant, Map<Triple, TripleConstraint> matching,
                                                    MatchingNotSatisfiedReason reasonIfNonConformant) {
        String m = isConformant ? "" :
                switch(reasonIfNonConformant) {
                    case TRIPLE_EXPRS -> "Shape not satisfied by the matching.";
                    case SEM_ACTS -> "Semantic actions not satisfied.";
                };
        addInfo(isConformant, m, matching);
    }

    /** Informs about the predicate-based pre-matching computed during validation of a triple expression, ie with every triple, associate the triple constraints having the same predicate.
     * Useful for precise error reporting.
     * The map given as parameter might change during validation, so any implementation that uses the pre-matching should make a copy of this map. */
    default void informPredicateBasedPreMatching(Map<Triple, List<TripleConstraint>> predicateBasedPreMatching) {}

    /** Informs about the pre-matching computed during validation of a triple expression, ie with every triple, associate the triple constraints that this triple satisfies, both w.r.t. the predicate and the value constraint.
     * Useful for precise error reporting.
     * The map given as parameter might change during validation, so any implementation that uses the pre-matching should make a copy of this map. */
    default void informPreMatching(Map<Triple, List<TripleConstraint>> cleanPreMatching) {}

    /** In the case when the expression is a Shape, inform about the triple expressions against which it is being
     * validated. In particular, these are the expressions against which the mappings are defined. */
    default void informShapeTripleExpressions(Map<Node, TripleExprForValidation> expressions) {}
    
    
    enum UnexpectedTriplesReason { CLOSED, EXTRA }

    enum MatchingNotSatisfiedReason { TRIPLE_EXPRS, SEM_ACTS }

}
