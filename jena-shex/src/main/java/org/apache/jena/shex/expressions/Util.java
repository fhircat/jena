package org.apache.jena.shex.expressions;

import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.eval.TripleExprAccumulationVisitor;

import java.util.ArrayList;
import java.util.List;

public class Util {

    public static List<TripleConstraint> collectTripleConstraints (TripleExpr tripleExpr,
                                                                   boolean traverseReferences, ShexSchema schema) {

        List<TripleConstraint> acc = new ArrayList<>();

        TripleExprAccumulationVisitor<TripleConstraint> step = new TripleExprAccumulationVisitor<>(acc) {
            @Override
            public void visit(TripleConstraint tripleConstraint) {
                accumulate(tripleConstraint);
            }
        };

        accumulate(tripleExpr, step, traverseReferences, schema);
        return acc;
    }

    public static <T> void accumulate (TripleExpr tripleExpr, TripleExprAccumulationVisitor<T> stepVisitor,
                                 boolean followTripleExprRefs, ShexSchema schema) {
        VoidWalker walker = new VoidWalker.Builder()
                .processTripleExprsWith(stepVisitor)
                .followTripleExprRefs(schema)
                .build();
        tripleExpr.visit(walker);
    }

}
