package org.apache.jena.shex;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.validation.ReportInfo;

import java.util.List;
import java.util.Set;

public interface ShexReport2 {

    // TODO to be replaced by a factory method
    ShexReport2 create(Node node, ShapeExpr shapeExpr);

    Node getNode();
    ShapeExpr getShapeExpr();
    ShexStatus getStatus();

    void setSatisfies(boolean satisfies);

    ShexReport2 getParent();
    void setParent(ShexReport2 parent);  // TODO can be set only once

    List<ShexReport2> getChildren();

    // TODO these create a ReportInfo, for success with some dummy message
    // subNeighbourhood is null if non relevant, TODO javadoc
    // subExpr is null if non relevant, TODO make it precise and javadoc
    void addInfoSuccess(String message, Expression subExpr, Set<Triple> subNeighbourhood);
    void addInfoFailure(String errorMessage, Expression expr, Set<Triple> subNeighbourhood);

    List<ReportInfo> getInfos();

    default boolean hasReports() {
        return getStatus() == ShexStatus.conformant;
    }
}
