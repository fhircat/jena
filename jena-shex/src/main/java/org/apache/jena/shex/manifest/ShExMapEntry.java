package org.apache.jena.shex.manifest;

import java.util.Map;

public class ShExMapEntry extends ManifestEntry {

    public static ShExMapEntry newEntry() {
        return new ShExMapEntry();
    }

    public static ShExMapEntry newEntry(Map<String, SourcedString> nvps) {
        ShExMapEntry entry = newEntry();
        entry.configureState(nvps);
        return entry;
    }

    public void configureState(Map<String, SourcedString> nvps) {
        super.configureState(nvps);
        //process additional items here
    }
}
