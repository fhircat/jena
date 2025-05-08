package org.apache.jena.shex;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.validation.ShexReport;

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
        if ( schema == null )
            throw new IllegalArgumentException("Schema cannot be null.");
        if ( graph == null )
            throw new IllegalArgumentException("Graph cannot be null.");
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Validate a shape map.
     */
    public ShexReport validate(ShapeMap shapeMap, Graph graph, ShexSchema schema);

    /**
     * Validate a specific node (the focus), with a specific shape.
     */
    public ShexReport validate(Node shapeLabel, Node focus, Graph graphData, ShexSchema schema);

    //public AShexReport validate(ShapeDecl shape, Node focus, Graph graphData, ShexSchema schema);

    //public AShexReport validate(ShapeMap shapeMap, Node dataNode, Graph dataGraph, ShexSchema shapes);

}

