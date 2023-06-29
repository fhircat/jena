package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeDecl;

import java.util.StringJoiner;

public class THVertex {

    public final Node label;
    public final ShapeDecl shapeDecl;

    THVertex(Node label, ShapeDecl shapeDecl) {
        this.label = label;
        this.shapeDecl = shapeDecl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof THVertex)) return false;

        THVertex thNode = (THVertex) o;

        return label.equals(thNode.label);
    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }

    @Override
    public String toString() {
        return THVertex.class.getSimpleName() + "[" + label + "]";
    }
}
