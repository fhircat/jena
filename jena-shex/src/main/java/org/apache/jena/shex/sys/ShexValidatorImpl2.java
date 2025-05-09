package org.apache.jena.shex.sys;

import com.fasterxml.jackson.core.JsonpCharacterEscapes;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.*;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.validation.ShexReportElement;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.validation.*;

import java.util.List;
import java.util.Map;

public class ShexValidatorImpl2 implements ShexValidator {

    private final Map<String, SemanticActionPlugin> semanticActionPluginIndex;

    public ShexValidatorImpl2(Map<String, SemanticActionPlugin> pz) {
        semanticActionPluginIndex = pz;
    }

    private ShexReport validate (Graph graph, ShexSchema schema, List<ShapeMapElement> shapeMap) {
        schema = schema.importsClosure();
        // TODO for now without memoization
        ValidationContext2 vCxt = new ValidationContext2(schema, graph,
                false, false, semanticActionPluginIndex);
        ShexReportElement factory = ExhaustiveShexReportElement.factory(); // TODO should be a parameter of the validator
        ShexReport.Builder builder = ShexReport.builder();
        for (ShapeMapElement e : shapeMap) {
            ShexReportElement r = vCxt.validate(e.nodeSelector, ShapeExprRef.create(e.shapeExprLabel), factory);
            builder.addReport(e.nodeSelector, e.shapeExprLabel, r);
        }
        return builder.build();
    }

    @Override
    public ShexReport validate(Graph graph, ShexSchema schema, ShapeMap shapeMap) {
        if (!shapeMap.isFixed())
            throw new ShexException("Cannot validate a non-fixed shape map.");
        return validate(graph, schema, shapeMap.entries());
    }

    @Override
    public ShexReport validate(Graph graph, ShexSchema schema, Node shapeExprLabel, Node focus) {
        return validate(graph, schema, List.of(new ShapeMapElement(focus, shapeExprLabel)));
    }

    @Override
    public ShexReport validate(Graph graph, ShexSchema schema, ShapeDecl shapeDecl, Node focus) {
        return validate(graph, schema, List.of(new ShapeMapElement(focus, shapeDecl.getLabel())));
    }

    @Override
    public ShexReport validate(Graph dataGraph, ShexSchema shapes, ShapeMap shapeMap, Node dataNode) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
