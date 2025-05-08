package org.apache.jena.shex.sys;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.*;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.validation.*;

public class ShexValidatorImpl2 implements ShexValidator {


    public ShexReport2 validate(ShapeMap shapeMap, Graph graph, ShexSchema schema) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public ShexReport2 validate(Node shapeLabel, Node focus, Graph graphData, ShexSchema schema) {
        // TODO for now without memoization
        ValidationContext2 vCxt = new ValidationContext2(schema, graphData,
                false, false, null);
        return vCxt.validate(focus, ShapeExprRef.create(shapeLabel), ExhaustiveShexReporter.factory());
    }
}
