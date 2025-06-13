package org.apache.jena.shex.sys;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.*;
import org.apache.jena.shex.reporting.*;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.validation.*;

import java.util.List;
import java.util.Map;

public class ShexValidatorImpl2 implements ShexValidator {

    private final Map<String, SemanticActionPlugin> semanticActionPluginIndex;
    private Reporter reporter;

    public ShexValidatorImpl2(Map<String, SemanticActionPlugin> pz) {
        semanticActionPluginIndex = pz;
    }

    private ShexValidationReport validate (Graph graph, ShexSchema schema, List<ShapeMapElement> shapeMap) {
        schema = schema.importsClosure();
        // TODO for now without memoization
        ValidationContext2 vCxt = new ValidationContext2(schema, graph,
                false, false, semanticActionPluginIndex);
        boolean isValid = vCxt.dispatchStartSemanticAction(schema);
        ShexValidationReport.Builder builder = ShexValidationReport.builder();
        if (!isValid) {
            builder.addReport(new SimpleReport(ShexStatus.nonconformant, "Start semantic actions failed.", null));
            return builder.build();
        }
        for (ShapeMapElement e : shapeMap) {
            Report re = vCxt.validate(e.nodeSelector, e.shapeExprLabel, reporter);
            builder.addReport(e.nodeSelector, e.shapeExprLabel, re);
        }
        return builder.build();
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
