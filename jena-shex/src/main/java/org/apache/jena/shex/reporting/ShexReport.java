package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeMapElement;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.validation.ReportInfoImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShexReport {

    private final List<ReportElement> directReports;
    private final List<ShapeMapElement> mapElementsReports;

    private ShexReport(List<ShapeMapElement> mapElementsReports, List<ReportElement> directReports) {
        this.mapElementsReports = mapElementsReports;
        this.directReports = directReports;
    }

    public static Builder builder() {
        return new Builder();
    }

    // TODO quick fix
    public List<ShapeMapElement> getMapElementsReports() {
        return mapElementsReports;
    }

    public void forEachReport(Consumer<ShapeMapElement> action) {
        mapElementsReports.forEach(action);
    }

   public boolean conforms() {
        return mapElementsReports.stream().allMatch(it -> it.getStatus() == ShexStatus.conformant);
   }

   public static class Builder {

        private final List<ReportElement> directReports = new ArrayList<>();
        private final List<ShapeMapElement> shapeMapReports = new ArrayList<>();

        public Builder() { }

        public void addReport(ShapeMapElement shapeMapElement, NodeSatExprReport report) {
            shapeMapReports.add(shapeMapElement.createReportElement(report));
        }

        public void addReport(Node focusNode, Node shapeExprLabel, NodeSatExprReport report) {
            addReport(new ShapeMapElement(focusNode, shapeExprLabel), report);
        }

        public void addReport(ShexStatus status, String message) {
            directReports.add(new SimpleReportElement(message, status));
        }

        public ShexReport build() {
            return new ShexReport(shapeMapReports, directReports);
        }
    }
}
