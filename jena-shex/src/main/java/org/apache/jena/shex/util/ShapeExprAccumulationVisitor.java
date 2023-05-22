package org.apache.jena.shex.util;

import org.apache.jena.shex.expressions.VoidShapeExprVisitor;

import java.util.Collection;

public abstract class ShapeExprAccumulationVisitor<T> implements VoidShapeExprVisitor {

    private final Collection<T> acc;
    public ShapeExprAccumulationVisitor(Collection<T> acc) {
        this.acc = acc;
    }
    protected void accumulate(T element) {
        acc.add(element);
    }
}
