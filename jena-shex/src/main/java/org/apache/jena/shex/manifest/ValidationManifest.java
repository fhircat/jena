package org.apache.jena.shex.manifest;

import java.util.Map;
import java.util.Objects;

public class ValidationManifest extends Manifest<ValidationEntry> {
    public ValidationEntry newEntry (Map<String, SourcedString> nvps) {
        ValidationEntry entry = ValidationEntry.newEntry(nvps);
        addEntry(entry);
        return entry;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
