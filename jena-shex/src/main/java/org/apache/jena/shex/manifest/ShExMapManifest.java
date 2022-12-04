package org.apache.jena.shex.manifest;

import java.util.Map;

public class ShExMapManifest extends Manifest<ShExMapEntry> {
    public ShExMapEntry newEntry(Map<String, SourcedString> nvps) {
        ShExMapEntry entry = ShExMapEntry.newEntry(nvps);
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
