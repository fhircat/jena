package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeMapElement;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.validation.ReportInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ShexReport {

    private List<ShexReportElement> elements = new ArrayList<>();

    public void addElement(ShexReportElement element) {
        elements.add(element);
    }

    public boolean hasReports() {
        return elements.size() > 0;
    }

    public List<ShexReportElement> getElements() {
        return elements;
    }

    // TODO quick fix
    public void forEachReport(Consumer<ShapeMapElement> action) {
        throw new UnsupportedOperationException("not implemented yet");
        //elements.forEach(action);
    }

    // TODO quick fix
   public boolean conforms() {
        return elements.stream().allMatch(it -> it.getStatus() == ShexStatus.conformant);
   }

}
