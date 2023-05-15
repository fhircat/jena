package org.apache.jena.shex.eval;

import org.apache.jena.shex.expressions.*;

import java.util.Collection;

public abstract class TripleExprAccumulationVisitor2<T> implements TripleExprVisitor2<Void> {

    final Collection<T> acc;
    public TripleExprAccumulationVisitor2(Collection<T> acc) {
        this.acc = acc;
    }

    @Override
    public Void visit(TripleExprCardinality tripleExprCardinality) {
        return null;
    }

    @Override
    public Void visit(EachOf eachOf) {
        return null;
    }

    @Override
    public Void visit(OneOf oneOf) {
        return null;
    }

    @Override
    public Void visit(TripleExprEmpty tripleExprEmpty) {
        return null;
    }

    @Override
    public Void visit(TripleExprRef tripleExprRef) {
        return null;
    }

    @Override
    public Void visit(TripleConstraint tripleConstraint) {
        return null;
    }
}
