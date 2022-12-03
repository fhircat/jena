package org.apache.jena.shex.manifest;

public class ShExMapEntryManifest extends Manifest<ShExMapEntry> {

    public ShExMapEntry newEntry () {
        return ShExMapEntry.newEntry();
    }
}
