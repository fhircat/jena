package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.NodeConstraintComponent;
import org.apache.jena.shex.expressions.SemAct;

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

    default void addDescendantSatisfactionInfo(boolean isConformant, String message) {
        addInfo(message, null, isConformant);
    }

    default void addForbiddenExtraInfo(String message, Set<Triple> unallowedExtraTriples) {
        addInfo(message, unallowedExtraTriples, false);
    }

    default void addSemanticActionsInfo(boolean isConformant, String message, SemAct semAct) {
        addInfo(message, semAct, isConformant);
    }

    default void addNodeConstraintInvalidInfo(NodeConstraintComponent c, String message) {
        addInfo(message, c, false);
    }

    default void addNotClosedInfo(String message, Set<Triple> unmatchedTriples) {
        addInfo(message, unmatchedTriples, false);
    }

    default void addInfoExternalNotSupported() {
        addInfo("Shape external not supported.", null, false);
    }

    default void addInfoCycle() {
        addInfo("Cycle", null, true);
    }
}
