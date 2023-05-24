package org.apache.jena.shex.util;


import org.apache.jena.graph.Node;

public class UndefinedReferenceException extends RuntimeException {

    private final Node undefinedLabel;

    public UndefinedReferenceException(Node undefinedRefLabel, String message) {
        super(message);
        undefinedLabel = undefinedRefLabel;
    }

    public Node getUndefinedLabel () {
        return undefinedLabel;
    }
}
