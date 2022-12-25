package io.shex.jena.shexmap.manifest;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.jena.shex.*;
import org.apache.jena.shex.manifest.*;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.semact.ShExMapSemanticActionPlugin;

import java.nio.file.Path;
import java.util.Collections;
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
            assertEquals(entries.size(), 3);
            assertEquals(entries.get(0).getClass().getName(), ValidationEntry.class.getName());
            assertEquals(entries.get(1).getClass().getName(), ShExMapEntry.class.getName());
            assertEquals(entries.get(2).getClass().getName(), ShExMapEntry.class.getName());
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

            // Set up validation parameters
            ValidationParms parms = new ValidationParms(manifestEntry, base);

            // validate with ShExMap plugin
            ShExMapSemanticActionPlugin semActPlugin = new ShExMapSemanticActionPlugin(parms.schema);
            List<SemanticActionPlugin> semanticActionPlugins = Collections.singletonList(semActPlugin);
            ShexReport report = parms.validate(semanticActionPlugins);

            // Nothing works if it doesn't pass validation
            assertTrue(report.conforms());

            List<String> output = semActPlugin.getOut();
            System.out.println(String.join("\n", output));
            ShExMapSemanticActionPlugin.BindingNode tree = semActPlugin.getBindingTree();
            if (tree != null)
                semActPlugin.getBindingTreeAsJson(System.out);
        }
    }
}