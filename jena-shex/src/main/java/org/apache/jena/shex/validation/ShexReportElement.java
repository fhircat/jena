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

public interface ShexReportElement {

    // TODO to be replaced by a factory method
    ShexReportElement create(Node node, ShapeExpr shapeExpr);

    Node getNode();
    ShapeExpr getShapeExpr();
    ShexStatus getStatus();

    void setSatisfies(boolean satisfies);

    ShexReportElement getParent();
    void setParent(ShexReportElement parent);  // TODO can be set only once

    List<ShexReportElement> getChildren();

    // TODO these create a ReportInfo, for success with some dummy message
    // subNeighbourhood is null if non relevant, TODO javadoc
    // subExpr is null if non relevant, TODO make it precise and javadoc
    void addInfoSuccess(String message, Expression subExpr, Set<Triple> subNeighbourhood);
    void addInfoFailure(String errorMessage, Expression expr, Set<Triple> subNeighbourhood);

    List<ReportInfo> getInfos();

}
