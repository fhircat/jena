package org.apache.jena.shex.eval;

import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Util {

    public static List<TripleConstraint> collectTripleConstraints (TripleExpr tripleExpr,
                                                                   boolean traverseReferences, ShexSchema schema) {

        List<TripleConstraint> acc = new ArrayList<>();

        TripleExprAccumulationVisitor2<TripleConstraint> step = new TripleExprAccumulationVisitor2<>(acc) {
            @Override
            public Void visit(TripleConstraint tripleConstraint) {
                acc.add(tripleConstraint);
                return null;
            }
        };

        TripleExprWalker2 walker = new TripleExprWalker2(step, null, traverseReferences, schema);
        tripleExpr.visit(walker);
        return acc;
    }

    public static List<TripleExpr> collectSubExprWithSemActs (TripleExpr tripleExpr,
                                                              boolean traverseReferences, ShexSchema schema) {

        List<TripleExpr> acc = new ArrayList<>();

        TripleExprAccumulationVisitor2<TripleExpr> step = new TripleExprAccumulationVisitor2<>(acc) {

            private Void metaVisit (TripleExpr tripleExpr) {
                if (tripleExpr.getSemActs() != null)
                    acc.add(tripleExpr);
                return null;
            }

            @Override
            public Void visit(TripleExprCardinality tripleExprCardinality) {
                return metaVisit(tripleExprCardinality);
            }

            @Override
            public Void visit(EachOf eachOf) {
                return metaVisit(eachOf);
            }

            @Override
            public Void visit(OneOf oneOf) {
                return metaVisit(oneOf);
            }

            @Override
            public Void visit(TripleExprEmpty tripleExprEmpty) {
                return metaVisit(tripleExpr);
            }

            @Override
            public Void visit(TripleExprRef tripleExprRef) {
                return metaVisit(tripleExprRef);
            }

            @Override
            public Void visit(TripleConstraint tripleConstraint) {
                return metaVisit(tripleConstraint);
            }
        };

        TripleExprWalker2 walker = new TripleExprWalker2(step, null, traverseReferences, schema);
        tripleExpr.visit(walker);
        return acc;
    }

}
