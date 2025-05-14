package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeMapElement;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.ShapeExpr;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface ShexReportElementRemove {

    // TODO to be replaced by a factory method
    ShexReportElementRemove create(Node node, ShapeExpr shapeExpr);

    Node getNode();
    ShapeExpr getShapeExpr();
    ShexStatus getStatus();

    void setSatisfies(boolean satisfies);

    ShexReportElementRemove getParent();
    void setParent(ShexReportElementRemove parent);  // TODO can be set only once

    List<ShexReportElementRemove> getChildren();

    // TODO these create a ReportInfo, for success with some dummy message
    // subNeighbourhood is null if non relevant, TODO javadoc
    // subExpr is null if non relevant, TODO make it precise and javadoc
    void addInfoSuccess(String message, Expression subExpr, Set<Triple> subNeighbourhood);
    void addInfoFailure(String errorMessage, Expression expr, Set<Triple> subNeighbourhood);

    List<ReportInfo> getInfos();

}
