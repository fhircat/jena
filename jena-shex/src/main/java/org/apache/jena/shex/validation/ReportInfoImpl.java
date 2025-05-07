package org.apache.jena.shex.validation;

import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;

import java.util.Set;

public class ReportInfoImpl implements ReportInfo {
    private final Expression expr;
    private final Set<Triple> subNeighbourhood;
    private final ShexStatus status;
    private String message;
    public ReportInfoImpl(Expression expr, Set<Triple> subNeighbourhood, ShexStatus status, String message) {
        this.expr = expr;
        this.subNeighbourhood = subNeighbourhood;
        this.status = status;
        this.message = message;
    }

    @Override
    public Expression getExpression() {
        return null;
    }

    @Override
    public Set<Triple> getSubNeighbourhood() {
        return subNeighbourhood;
    }

    @Override
    public ShexStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "ReportInfoImpl{" +
                "expr=" + expr +
                ", subNeighbourhood=" + subNeighbourhood +
                ", status=" + status +
                ", message='" + message + '\'' +
                '}';
    }
}
