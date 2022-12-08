package io.shex.jena.shexmap.manifest;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.jena.shex.*;
import org.apache.jena.shex.manifest.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ShExMapManifestTest {
    static String base = "./src/test/files";

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new ShExMapManifestTest.RoundTripHeterogeneousShExMap());

        String manifestFile = "ShExMap-manifest.json";
        ValidationAndShExMapManifest manifest = new ValidationAndShExMapManifest();
        new ManifestReader().read(Path.of(base, manifestFile), manifest);

//        OutputStream out = new ByteArrayOutputStream();
//        new ManifestWriter().writeJson(out, manifest);
//        System.out.println(out);

        List<ManifestEntry> manifestEntries = manifest.getEntries();
        for (int i = 0; i < manifestEntries.size(); ++i) {
            ManifestEntry entry = manifestEntries.get(i);
            suite.addTest(entry instanceof ValidationEntry
                    ? new TestManifest.ManifestEntryTest(manifestFile, i, (ValidationEntry)entry)
                    : new ShExMapTest(manifestFile, i, (ShExMapEntry)entry)
            );
        }

        return suite;
    }

//    @Test
    static public class RoundTripHeterogeneousShExMap extends TestCase {

        RoundTripHeterogeneousShExMap() {
            super("RoundTripHeterogeneousShExMap");
        }

        public void runTest() {
            // Populate heterogeneous manifest from a file.
            Manifest manifest = new ValidationAndShExMapManifest();
            new ManifestReader().read(Path.of(base, "ShExMap-manifest.json"), manifest);

            // Check against some structural expectations.
            List<ManifestEntry> entries = manifest.getEntries();
            assertEquals(entries.size(), 2);
            assertEquals(entries.get(0).getClass().getName(), ValidationEntry.class.getName());
            assertEquals(entries.get(1).getClass().getName(), ShExMapEntry.class.getName());
        }
    }

    private static class ValidationAndShExMapManifest extends Manifest<ManifestEntry> {
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

    static public class ShExMapTest extends TestManifest.ManifestEntryTest {

        public ShExMapTest(String manifestFile, int manifestIndex, ManifestEntry entry) {
            super(manifestFile, manifestIndex, entry);
        }

        @Override
        public void runTest() {
//            System.out.println(getName());

            ValidationParms parms = new ValidationParms(manifestEntry, base);
            ShexReport report = parms.validate();
            assertTrue(report.conforms());
        }
    }
}