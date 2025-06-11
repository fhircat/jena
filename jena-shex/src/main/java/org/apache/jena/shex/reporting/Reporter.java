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
    void addChild(Node node, Expression expr, Set<Triple> neigh, Reporter child);

    void setResult(ShexStatus status, String message, Object details);
    void addInfo(ShexStatus status, String message, Object details);

    Report getReport();

    /** Returns yes if the reporter is not interested in detailed reports.
     * Used to speed up error reporting. */
    boolean isValidateOnly();

    default boolean setIsConformant(boolean isConformant, String message, Object details) {
        setResult(isConformant ? ShexStatus.conformant : ShexStatus.nonconformant, message, details);
        return isConformant;
    }

    default boolean setIsConformant(boolean isConformant, String message) {
        setResult(isConformant ? ShexStatus.conformant : ShexStatus.nonconformant, message, null);
        return isConformant;
    }
    /** Sets the result to conformant or non-conformant, and returns the boolean. */
    default boolean setIsConformant(boolean isConformant) {
        setResult(isConformant ? ShexStatus.conformant : ShexStatus.nonconformant, "", null);
        return isConformant;
    }

    default void addSemanticActionsInfo(ShexStatus status, String message, SemAct semAct) {
        addInfo(status, message, semAct);
    }

    default void addNodeConstraintInvalidInfo(NodeConstraintComponent c, String message) {
        addInfo(ShexStatus.nonconformant, message, c);
    }
}
