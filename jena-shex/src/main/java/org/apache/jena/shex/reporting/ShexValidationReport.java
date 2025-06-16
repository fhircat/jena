package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeMapElement;
import org.apache.jena.shex.ShexStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShexValidationReport {

    private final List<ShapeMapElement> mapElementsReports = new ArrayList<>();

    public void forEachReport(Consumer<ShapeMapElement> action) {
        mapElementsReports.forEach(action);
    }

    public boolean conforms() {
        return mapElementsReports.stream().allMatch(it -> it.getStatus() == ShexStatus.conformant);
    }

    public void setReport (ShapeMapElement shapeMapElement, Report report) {
        mapElementsReports.add(
                new ShapeMapElement(shapeMapElement.nodeSelector, shapeMapElement.shapeExprLabel).createReportElement(report));
    }

    public void setStartSemanticActionReport (Report report) {
        mapElementsReports.add(
                new ShapeMapElement((Node) null, null).createReportElement(report));
    }

}
