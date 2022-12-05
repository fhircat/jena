package io.shex.jena.shexmap.manifest;

import org.apache.jena.shex.manifest.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ShExMapManifestTest {
    static String base = "./src/test/files";

    @Test
    public void RoundTripHeterogeneousShExMap() {

        // Populate heterogeneous manifest from a file.
        Manifest manifest = new ValidationAndShExMapManifest();
        new ManifestReader().read(Path.of(base, "ShExMap-manifest.json"), manifest);

        // Check against some structural expectations.
        List<ManifestEntry> entries = manifest.getEntries();
        assertEquals(entries.size(), 2);
        assertEquals(entries.get(0).getClass().getName(), ValidationEntry.class.getName());
        assertEquals(entries.get(1).getClass().getName(), ShExMapEntry.class.getName());

        // Write it out.
        OutputStream out = new ByteArrayOutputStream();
        new ManifestWriter().writeJson(out, manifest);

        // Implement a second manifest.
        Manifest manifest2 = new ValidationAndShExMapManifest();

        // Populate it from first's output.
        InputStream in2 = new ByteArrayInputStream(out.toString().getBytes());
        new ManifestReader().read(in2, base, manifest2);

        // They should be equivalent.
        assertEquals(manifest2, manifest);
    }

    private class ValidationAndShExMapManifest extends Manifest<ManifestEntry> {
        @Override
        public ManifestEntry newEntry(Map<String, SourcedString> nvps) {
            ManifestEntry newEntry;
            if (nvps.containsKey(ShExMapEntry.KEY_OUTPUT_SCHEMA))
                newEntry = new ShExMapEntry(nvps);
            else
                newEntry = new ValidationEntry(nvps);
            addEntry(newEntry);
            return newEntry;
        }
    }
}