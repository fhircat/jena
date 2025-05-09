package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeMapElement;
import org.apache.jena.shex.ShexStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShexReport {

    private final List<ShapeMapElement> reports;

    private ShexReport(List<ShapeMapElement> reports) {
        this.reports = reports;
    }

    public static Builder builder() {
        return new Builder();
    }

    // TODO quick fix
    public List<ShapeMapElement> getReports() {
        return reports;
    }

    public void forEachReport(Consumer<ShapeMapElement> action) {
        reports.forEach(action);
    }

   public boolean conforms() {
        return reports.stream().allMatch(it -> it.getStatus() == ShexStatus.conformant);
   }

   public static class Builder {

        private final List<ShapeMapElement> reports = new ArrayList<>();

        public Builder() { }

        public void addReport(ShapeMapElement shapeMapElement, ShexReportElement report) {
            reports.add(shapeMapElement.createReportElement(report));
        }

        public void addReport(Node focusNode, Node shapeExprLabel, ShexReportElement report) {
            addReport(new ShapeMapElement(focusNode, shapeExprLabel), report);
        }

        public ShexReport build() {
            return new ShexReport(reports);
        }
    }
}
