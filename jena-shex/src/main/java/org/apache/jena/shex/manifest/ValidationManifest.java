package org.apache.jena.shex.manifest;

import java.util.Map;

public class ValidationManifest extends Manifest<ValidationEntry> {

    public ValidationEntry newEntry () {
        ValidationEntry entry = ValidationEntry.newEntry();
        addEntry(entry);
        return entry;
    }

    public ValidationEntry newEntry (Map<String,String> nvps) {
        ValidationEntry entry = ValidationEntry.newEntry(nvps);
        addEntry(entry);
        return entry;
    }
}
