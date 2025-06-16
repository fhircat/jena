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

    void setResult(ShexStatus status, String message, Object details);
    void addInfo(ShexStatus status, String message, Object details);

    Report getReport();

    /** Returns yes if the reporter is not interested in detailed reports.
     * Used to speed up error reporting. */
    boolean isValidateOnly();

    /** Sets the result to conformant or non-conformant, and returns the boolean. */
    default boolean setIsConformant(boolean isConformant) {
        setResult(isConformant ? ShexStatus.conformant : ShexStatus.nonconformant, "", null);
        return isConformant;
    }

    default void addDescendantSatisfactionInfo(boolean isConformant, String message) {
        addInfo(isConformant ? ShexStatus.conformant : ShexStatus.nonconformant, message, null);
    }

    default void addForbiddenExtraInfo(String message, Set<Triple> unallowedExtraTriples) {
        addInfo(ShexStatus.nonconformant, message, unallowedExtraTriples);
    }

    default void addSemanticActionsInfo(boolean isConformant, String message, SemAct semAct) {
        addInfo(isConformant ? ShexStatus.conformant : ShexStatus.nonconformant, message, semAct);
    }

    default void addNodeConstraintInvalidInfo(NodeConstraintComponent c, String message) {
        addInfo(ShexStatus.nonconformant, message, c);
    }

    default void addNotClosedInfo(String message, Set<Triple> unmatchedTriples) {
        addInfo(ShexStatus.nonconformant, message, unmatchedTriples);
    }

    default void addInfoExternalNotSupported() {
        addInfo(ShexStatus.nonconformant, "Shape external not supported.", null);
    }

    default void addInfoCycle() {
        addInfo(ShexStatus.conformant, "Cycle", null);
    }
}
