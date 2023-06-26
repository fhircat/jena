package org.apache.jena.shex.expressions;

public abstract class Expression {

    public final int id;
    private static int nextId = 0;

    public Expression () {
        this.id = nextId++;
    }

}
