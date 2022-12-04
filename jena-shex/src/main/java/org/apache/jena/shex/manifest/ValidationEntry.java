package org.apache.jena.shex.manifest;

import java.util.Map;

public class ValidationEntry extends ManifestEntry {

    public static ValidationEntry newEntry(Map<String,SourcedString> nvps) {
        ValidationEntry validationEntry = new ValidationEntry();
        validationEntry.configureState(nvps);
        return validationEntry;
    }

    public void configureState(Map<String, SourcedString> nvps) {
        super.configureState(nvps);
        //process additional items here
    }
}
