package org.apache.jena.shex.manifest;

import java.util.Map;

public class ValidationEntry extends ManifestEntry {

    public static ValidationEntry newEntry() {
        return new ValidationEntry();
    }
    public static ValidationEntry newEntry(Map<String,String> nvps) {
        ValidationEntry validationEntry = newEntry();
        validationEntry.configureState(nvps);
        return validationEntry;
    }

    public void configureState(Map<String,String> nvps) {
        super.configureState(nvps);
        //process additional items here
    }
}
