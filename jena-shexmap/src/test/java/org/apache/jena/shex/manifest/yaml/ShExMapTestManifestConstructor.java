package org.apache.jena.shex.manifest.yaml;

import org.apache.jena.shex.manifest.Manifest;
import org.apache.jena.shexmap.manifest.ShExMapManifest;
import org.apache.jena.shexmap.manifest.ShExMapManifestEntry;
import org.apache.jena.shexmap.manifest.ShExMapTestManifestEntry;

import java.util.List;

public class ShExMapTestManifestConstructor extends AbstractManifestConstructor<ShExMapTestManifestEntry> {

    public ShExMapTestManifestConstructor() {
        super(ShExMapTestManifestEntry.class);
    }
    @Override
    protected Manifest constructManifest(List<ShExMapTestManifestEntry> entries) {
        ShExMapManifest manifest = new ShExMapManifest();
        for(ShExMapManifestEntry entry : entries) {
            manifest.addEntry(entry);
        }
        return manifest;
    }
}
