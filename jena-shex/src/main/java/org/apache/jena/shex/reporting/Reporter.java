package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;

import java.util.Set;

public interface Reporter {

    /** Report for checking node against shape expr. label. */
    Reporter createNew (Node node, Node label);
    void setFinalResult(boolean isValid); // TODO to be removed
    ReportElement getReport();

    /** While validating n1 against e1, needed to check n2 against e2. */
    void addChild(Node n1, Expression e1, Node n2, Expression e2, Set<Triple> n2subNeigh);

    /** The result of validating node against eexpr, with possible sub-neighbourhood for node. With additional details. */
    void setResult(Node node, Expression expr, Set<Triple> subNeigh, ShexStatus status, String message, Object details);

    /** The result of validating node against expr, with possible sub-neighbourhood for node. */
    default void setResult(Node node, Expression expr, Set<Triple> subNeigh, ShexStatus status, String message) {
        setResult(node, expr, subNeigh, status, message, null);
    }

    /** node with possible sub-neighbourhood satisfies expr, empty message. */
    default void setValid(Node node, Expression expr, Set<Triple> subNeigh) {
        setResult(node, expr, subNeigh, ShexStatus.conformant, "");
    }
    /** node with possible sub-neighbourhood does not satisfy expr, empty message. */
    default void setInvalid(Node node, Expression expr, Set<Triple> subNeigh) {
        setResult(node, expr, subNeigh, ShexStatus.nonconformant, "");
    }

    void setInvalid(Node node, NodeConstraintComponent e, String m);

    // Semantic actions
    void setResult(Node node, ShapeExpr expr, SemAct semAct, ShexStatus status);
    void setResult(Set<Triple> triples, TripleExpr expr, SemAct semAct, ShexStatus status);
    void setResult(ShexSchema schema, SemAct semAct, ShexStatus status);

    void setResult(ShexStatus status, String message);



}
