package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.Expression;

import java.util.Objects;
import java.util.Set;


/** Report on validating a node or a part of node's neighbourhood against an {@link Expression}. */
public class ExpressionReport extends SimpleReport {
    protected final Node node;
    protected final Expression expr;
    private Set<Triple> subNeigh;

    /** Creates a report.
     * @param node
     * @param expr
     * @param subNeigh null if the report is about the node
     */
    public ExpressionReport(Node node, Expression expr, Set<Triple> subNeigh) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(expr);
        this.node = node;
        this.expr = expr;
        this.subNeigh = subNeigh;
    }
    public Node getNode() {
        return node;
    }
    public Expression getExpr() {
        return expr;
    }

    public Set<Triple> getSubNeigh() {
        return subNeigh;
    }
}
