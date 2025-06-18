package org.apache.jena.shex.reporting;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Reporter {

    Reporter createRoot(Node node, Expression expr);
    Reporter createChild(Node node, Expression expr, Set<Triple> neigh);
    void setReferenceTo (Report report, String additionalMessage);

    void setResult(ShexStatus status);
    void addInfo(String message, Object details, boolean isConformant);

    Report getReport();

    /** Returns yes if the reporter is not interested in detailed reports.
     * Used to speed up error reporting. */
    boolean isValidateOnly();

    /** Sets the result to conformant or non-conformant, and returns the boolean. */
    default boolean setIsConformant(boolean isConformant) {
        setResult(isConformant ? ShexStatus.conformant : ShexStatus.nonconformant);
        return isConformant;
    }

    // TODO which of the following facility methods should be implemented here, and which in the particular reporters ?

    default void addDescendantSatisfactionInfo(boolean isConformant, String message) {
        addInfo(message, null, isConformant);
    }

    default void addSemanticActionsInfo(boolean isConformant, String message, SemAct semAct) {
        addInfo(message, semAct, isConformant);
    }

    default void addNodeConstraintInvalidInfo(NodeConstraintComponent c, String message) {
        addInfo(message, c, false);
    }

    default void addInfoExternalNotSupported() {
        addInfo("Shape external not supported.", null, false);
    }

    default void addInfoCycle() {
        addInfo("Cycle", null, true);
    }




    default void informUnmatchableTriplesClosedShape(Set<Triple> unmatchableTriples) {
        addInfo("CLOSED required but forbidden triples", unmatchableTriples, false);
    }

    default void informMatchableTriples(Map<Triple, List<TripleConstraint>> predicateBasedPreMatching) {}

    default void informMatchedTriples(Map<Triple, List<TripleConstraint>> cleanPreMatching) {}

    default void informUnmatchedTriples(Set<Triple> unmatchedTriples) {
        addInfo("Unmatched triples.", unmatchedTriples, false);
    }

    default void informCandidateMatching(Map<Triple, TripleConstraint> matching) {}

    default void informCandidateMatchingFailedForTripleExpression(Map<Triple, TripleConstraint> matching, TripleExpr tripleExpr) {
        addInfo("Triple expression not satisfied by the matching", new Pair<>(tripleExpr, matching), false);
    }

}
