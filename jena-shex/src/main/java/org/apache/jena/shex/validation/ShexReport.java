package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeMapElement;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.validation.ReportInfo;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface ShexReport {

    // TODO to be replaced by a factory method
    ShexReport create(Node node, ShapeExpr shapeExpr);

    Node getNode();
    ShapeExpr getShapeExpr();
    ShexStatus getStatus();

    void setSatisfies(boolean satisfies);

    ShexReport getParent();
    void setParent(ShexReport parent);  // TODO can be set only once

    List<ShexReport> getChildren();

    // TODO these create a ReportInfo, for success with some dummy message
    // subNeighbourhood is null if non relevant, TODO javadoc
    // subExpr is null if non relevant, TODO make it precise and javadoc
    void addInfoSuccess(String message, Expression subExpr, Set<Triple> subNeighbourhood);
    void addInfoFailure(String errorMessage, Expression expr, Set<Triple> subNeighbourhood);

    List<ReportInfo> getInfos();

    default boolean hasReports() {
        return getStatus() == ShexStatus.conformant;
    }

    // TODO quick fix
    default void forEachReport(Consumer<ShapeMapElement> action) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    // TODO quick fix
    default boolean conforms() {
        return getStatus() == ShexStatus.conformant;
    }

}
