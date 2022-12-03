package org.apache.jena.shex.manifest;

public class ValidationEntryManifest extends Manifest<ValidationEntry> {

    public ValidationEntry newEntry () {
        return ValidationEntry.newEntry();
    }
}
