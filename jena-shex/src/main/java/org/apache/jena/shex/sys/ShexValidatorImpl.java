package org.apache.jena.shex.sys;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.*;
import org.apache.jena.shex.reporting.*;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.validation.*;

import java.util.List;
import java.util.Map;

public class ShexValidatorImpl implements ShexValidator {

    private final Map<String, SemanticActionPlugin> semanticActionPluginIndex;
    private Reporter reporter;

    public ShexValidatorImpl(Map<String, SemanticActionPlugin> pz) {
        semanticActionPluginIndex = pz;
    }

    private ShexValidationReport validate (Graph graph, ShexSchema schema, List<ShapeMapElement> shapeMap) {
        schema = schema.importsClosure();
        // TODO for now without memoization
        ValidationContext vCxt = new ValidationContext(schema, graph,
                false, false, semanticActionPluginIndex);
        ShexValidationReport resultReport = new ShexValidationReport();
        if (! vCxt.dispatchStartSemanticAction(schema)) {
            resultReport.setStartSemanticActionReport(new SimpleReport(ShexStatus.nonconformant,
                    "Start semantic actions failed."));
            return resultReport;
        }
        for (ShapeMapElement shapeMapElement : shapeMap) {
            Report re = vCxt.validate(shapeMapElement.nodeSelector, shapeMapElement.shapeExprLabel, reporter);
            resultReport.setReport(shapeMapElement, re);
        }
        return resultReport;
    }

    /** Is effective at the next validation. */
    public void setReporter(Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public ShexValidationReport validate(Graph graph, ShexSchema schema, ShapeMap shapeMap) {
        if (!shapeMap.isFixed())
            throw new ShexException("Cannot validate a non-fixed shape map.");
        return validate(graph, schema, shapeMap.entries());
    }

    @Override
    public ShexValidationReport validate(Graph graph, ShexSchema schema, Node shapeExprLabel, Node focus) {
        return validate(graph, schema, List.of(new ShapeMapElement(focus, shapeExprLabel)));
    }

    @Override
    public ShexValidationReport validate(Graph graph, ShexSchema schema, ShapeDecl shapeDecl, Node focus) {
        return validate(graph, schema, List.of(new ShapeMapElement(focus, shapeDecl.getLabel())));
    }

    @Override
    public ShexValidationReport validate(Graph dataGraph, ShexSchema shapes, ShapeMap shapeMap, Node dataNode) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
