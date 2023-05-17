package org.apache.jena.shex.eval;

import org.apache.jena.shex.expressions.*;

import java.util.Collection;

public abstract class TripleExprAccumulationVisitor<T> implements VoidTripleExprVisitor {

    private final Collection<T> acc;
    public TripleExprAccumulationVisitor(Collection<T> acc) {
        this.acc = acc;
    }
    protected void accumulate(T element) {
        acc.add(element);
    }
}
