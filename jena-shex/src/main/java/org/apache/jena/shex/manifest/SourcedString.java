package org.apache.jena.shex.manifest;

import java.net.URI;

public class SourcedString {
    protected URI source;
    protected String value;

    public SourcedString(URI source, String value) {
        this.source = source;
        this.value = value;
    }

    public URI getSource() {
        return source;
    }

    public String getValue() {
        return value;
    }
}
