package org.apache.jena.shex.manifest;

import java.util.Map;

public class ShExMapManifest extends Manifest<ShExMapEntry> {

    public ShExMapEntry newEntry () {
        ShExMapEntry entry = ShExMapEntry.newEntry();
        addEntry(entry);
        return entry;
    }

    public ShExMapEntry newEntry(Map<String,String> nvps) {
        ShExMapEntry entry = ShExMapEntry.newEntry(nvps);
        addEntry(entry);
        return entry;
    }
}
