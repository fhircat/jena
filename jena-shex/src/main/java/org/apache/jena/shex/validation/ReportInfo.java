package org.apache.jena.shex.validation;

import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;

import java.util.Set;

/**
 * An element of a report
 */
public interface ReportInfo {

    Expression getExpression();
    Set<Triple> getSubNeighbourhood();
    ShexStatus getStatus();
    String getMessage();
}
