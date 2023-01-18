package org.apache.jena.shex.manifest.yaml;

import org.apache.jena.shex.manifest.Manifest;
import org.apache.jena.shex.manifest.ValidationEntry;
import org.apache.jena.shex.manifest.ValidationManifest;

import java.util.List;

public class ValidationManifestConstructor extends AbstractManifestConstructor<ValidationEntry> {

    public ValidationManifestConstructor() {
        super(ValidationEntry.class);
    }
    @Override
    protected Manifest constructManifest(List<ValidationEntry> entries) {
        Manifest manifest = new ValidationManifest();
        for(ValidationEntry entry : entries) {
            manifest.addEntry(entry);
        }
        return manifest;
    }
}
