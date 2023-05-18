package org.apache.jena.shex.eval;

import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.TripleExpr;

import java.util.HashMap;
import java.util.Map;

public class SorbeHandler {

    private final Map<TripleExpr, SorbeTripleExpr> sourceToSorbeMap = new HashMap<>();

    /*package*/ SorbeTripleExpr getSorbe (TripleExpr tripleExpr, ShexSchema schema) {
        return sourceToSorbeMap.computeIfAbsent(tripleExpr, e -> SorbeTripleExpr.create(tripleExpr, schema));
    }
}
