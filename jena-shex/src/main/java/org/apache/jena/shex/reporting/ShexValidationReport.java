package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeMapElement;
import org.apache.jena.shex.ShexStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShexValidationReport {

    private final List<Report> directReports;
    private final List<ShapeMapElement> mapElementsReports;

    public static Builder builder() {
        return new Builder();
    }

    private ShexValidationReport(List<ShapeMapElement> mapElementsReports, List<Report> directReports) {
        this.mapElementsReports = mapElementsReports;
        this.directReports = directReports;
    }

    public void forEachReport(Consumer<ShapeMapElement> action) {
        mapElementsReports.forEach(action);
        // TODO should treat direct reports as well
    }

    public boolean conforms() {
        return mapElementsReports.stream().allMatch(it -> it.getStatus() == ShexStatus.conformant)
                && directReports.stream().allMatch(it -> it.getStatus() == ShexStatus.conformant);
    }

   public static class Builder {

        private final List<Report> directReports = new ArrayList<>();
        private final List<ShapeMapElement> shapeMapReports = new ArrayList<>();

        public Builder() { }

        public void addReport(ShapeMapElement shapeMapElement, Report report) {
            shapeMapReports.add(shapeMapElement.createReportElement(report));
        }

        public void addReport(Node focusNode, Node shapeExprLabel, Report report) {
            addReport(new ShapeMapElement(focusNode, shapeExprLabel), report);
        }

        public void addReport(Report report) {
            directReports.add(report);
        }

        public ShexValidationReport build() {
            return new ShexValidationReport(shapeMapReports, directReports);
        }
    }
}
