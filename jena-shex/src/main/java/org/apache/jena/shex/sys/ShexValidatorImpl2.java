package org.apache.jena.shex.sys;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.*;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.validation.ShexReportElement;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.validation.*;

import java.util.Map;

public class ShexValidatorImpl2 implements ShexValidator {

    private Map<String, SemanticActionPlugin> semanticActionPluginIndex;

    public ShexValidatorImpl2(Map<String, SemanticActionPlugin> pz) {
        semanticActionPluginIndex = pz;
    }

    @Override
    public ShexReport validate(Graph graph, ShexSchema shapes, ShapeMap shapeMap) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public ShexReport validate(Graph graphData, ShexSchema schema, Node shapeExprLabel, Node focus) {
        schema = schema.importsClosure();
        // TODO for now without memoization
        ValidationContext2 vCxt = new ValidationContext2(schema, graphData,
                false, false, semanticActionPluginIndex);
        ShexReportElement factory = ExhaustiveShexReportElement.factory();
        ShexReportElement r = vCxt.validate(focus, ShapeExprRef.create(shapeExprLabel), factory);
        ShexReport report = new ShexReport();
        report.addElement(r);
        return report;
    }

    @Override
    public ShexReport validate(Graph graphData, ShexSchema schema, ShapeDecl shapeDecl, Node focus) {
        return validate(graphData, schema, shapeDecl.getLabel(), focus);
    }

    @Override
    public ShexReport validate(Graph dataGraph, ShexSchema shapes, ShapeMap shapeMap, Node dataNode) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
