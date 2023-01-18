package org.apache.jena.shex.manifest.yaml;

import org.apache.jena.shex.manifest.Manifest;
import org.apache.jena.shexmap.manifest.ShExMapManifestEntry;
import org.apache.jena.shexmap.manifest.ShExMapManifest;

import java.util.List;

public class ShExMapManifestConstructor extends AbstractManifestConstructor<ShExMapManifestEntry> {

    public ShExMapManifestConstructor() {
        super(ShExMapManifestEntry.class);
    }
    @Override
    protected Manifest constructManifest(List<ShExMapManifestEntry> entries) {
        ShExMapManifest manifest = new ShExMapManifest();
        for(ShExMapManifestEntry entry : entries) {
            manifest.addEntry(entry);
        }
        return manifest;
    }
}
