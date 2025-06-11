package org.apache.jena.shex;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.validation.ShexValidationReport;

import java.util.Objects;

public interface ShexValidator2 {

    /** Basic validator that can be used with every schema and every graph. */
    public static ShexValidator2 get () {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** A validator that can be used only with the given schema. */
    public static ShexValidator2 get(ShexSchema schema) {
        if ( schema == null )
            throw new IllegalArgumentException("Schema cannot be null.");
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** A validator that can be used only with the given schema and graph. */
    public static ShexValidator2 get(ShexSchema schema, Graph graph) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(graph);
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Validate a shape map.
     */
    public ShexValidationReport validate(ShapeMap shapeMap, Graph graph, ShexSchema schema);

    /**
     * Validate a specific node (the focus), with a specific shape.
     */
    public ShexValidationReport validate(Node shapeLabel, Node focus, Graph graphData, ShexSchema schema);

    //public AShexReport validate(ShapeDecl shape, Node focus, Graph graphData, ShexSchema schema);

    //public AShexReport validate(ShapeMap shapeMap, Node dataNode, Graph dataGraph, ShexSchema shapes);

}

