/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.shex;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.jena.atlas.json.io.JSWriter;
import org.apache.jena.shex.manifest.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.assertEquals;

public final class TestManifest {
    static String base = "./src/test/files";

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new SmallParsedVsManual());
        suite.addTest(new HeterogenousLocal());

        ValidationManifest manifest = new ValidationManifest();
        new ManifestReader().read(Path.of(base, "Validation-manifest.json"), manifest);

//        OutputStream out = new ByteArrayOutputStream();
//        new ManifestWriter().writeJson(out, manifest);
//        System.out.println(out);

        for (ManifestEntry entry: manifest.getEntries()) {
            OutputStream eltJson = new ByteArrayOutputStream();
            JSWriter writer = new JSWriter(eltJson);
            writer.startOutput();
            writer.startObject();
            entry.writeJson(writer);
            writer.finishObject();
            writer.finishOutput();
            System.out.println(eltJson);

            suite.addTest(new ValidationTest(1, 1));
        }

        return suite;
    }

    static public class SmallParsedVsManual extends TestCase {
        SmallParsedVsManual() { super("SmallParsedVsManual"); }
        public void runTest() {
            ValidationManifest parsed = new ValidationManifest();
            new ManifestReader().read(Path.of(base, "small-manifest.json"), parsed);
            ValidationManifest manual = new ValidationManifest();
            Map<String, SourcedString> nvps = new HashMap<>();
            SourcedString label = new SourcedString(null, "clinical observation all refs");
            nvps.put(ValidationEntry.KEY_SCHEMA_LABEL, label);
            manual.addEntry(new ValidationEntry(nvps));
            assertEquals(parsed, manual);
        }
    }

    static public class HeterogenousLocal extends TestCase {
        HeterogenousLocal() { super("Heterogenous"); }
        public void runTest() {

            // Populate heterogeneous manifest from a string.
            MyManifest manifest = new MyManifest();
            String manifestJsonString = "[\n"
                + "  {\n"
                + "    \"schemaLabel\": \"clinical observation all refs\",\n"
                + "    \"dataLabel\": \"with birthdate\"\n"
                + "  },\n"
                + "  {\n"
                + "    \"schemaLabel\": \"clinical observation inline\",\n"
                + "    \"dataLabel999\": \"with birthdate\",\n"
                + "    \"outputSchema\": \"blah blah blah\"\n"
                + "  }\n"
                + "]\n";
            InputStream in = new ByteArrayInputStream(manifestJsonString.getBytes());
            new ManifestReader().read(in, base, manifest);

            // Check against some structural expectations.
            List<ManifestEntry> entries = manifest.getEntries();
            assertEquals(entries.size(), 2);
            assertEquals(entries.get(0).getClass().getName(), ValidationEntry.class.getName());
            assertEquals(entries.get(1).getClass().getName(), LocalEntry.class.getName());

            // Write it out.
            OutputStream out = new ByteArrayOutputStream();
            new ManifestWriter().writeJson(out, manifest);

            // Implement a second manifest.
            Manifest manifest2 = new MyManifest();

            // Populate it from first's output.
            InputStream in2 = new ByteArrayInputStream(out.toString().getBytes());
            new ManifestReader().read(in2, base, manifest2);

            // They should be equivalent.
            assertEquals(manifest2, manifest);
        }

        private class MyManifest extends Manifest<ManifestEntry> {
            @Override
            public ManifestEntry newEntry(Map<String, SourcedString> nvps) {
                ManifestEntry newEntry;
                if (nvps.containsKey(LocalEntry.KEY_OUTPUT_SCHEMA))
                    newEntry = new LocalEntry(nvps);
                else
                    newEntry = new ValidationEntry(nvps);
                addEntry(newEntry);
                return newEntry;
            }
        }

        private class LocalEntry extends ManifestEntry {
            public static final String KEY_OUTPUT_SCHEMA = "outputSchema";
            private String outputSchema;
            public static final String KEY_OUTPUT_SCHEMA_URL = "outputSchemaURL";
            private URI outputSchemaUrl;

            public LocalEntry(Map<String, SourcedString> nvps) {
                configureState(nvps);
            }

            public void configureState(Map<String, SourcedString> nvps) {
                super.configureState(nvps);
                Set<String> keys = nvps.keySet();
                for(String key: keys) {
                    switch (key) {
                        case KEY_OUTPUT_SCHEMA:
                            setOutputSchema(nvps.get(KEY_OUTPUT_SCHEMA).getValue());
                            setOutputSchemaUrl(nvps.get(KEY_OUTPUT_SCHEMA).getSource());
                            break;
                    }
                }
                //process additional items here
            }

            public void writeJson(JSWriter out) {
                super.writeJson(out);
                if (outputSchemaUrl != null) {
                    out.pair(KEY_OUTPUT_SCHEMA_URL, outputSchemaUrl.toString());
                } else if (outputSchema != null) {
                    out.pair(KEY_OUTPUT_SCHEMA, outputSchema);
                }
            }

            public String getOutputSchema() {
                return outputSchema;
            }
            public void setOutputSchema(String outputSchema) {
                this.outputSchema = outputSchema;
            }
            public URI getOutputSchemaUrl() {
                return outputSchemaUrl;
            }
            public void setOutputSchemaUrl(URI outputSchemaUrl) {
                this.outputSchemaUrl = outputSchemaUrl;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof LocalEntry)) return false;
                if (!super.equals(o)) return false;
                LocalEntry that = (LocalEntry) o;
                return Objects.equals(getOutputSchema(), that.getOutputSchema()) && Objects.equals(getOutputSchemaUrl(), that.getOutputSchemaUrl());
            }

            @Override
            public int hashCode() {
                return Objects.hash(super.hashCode(), getOutputSchema(), getOutputSchemaUrl());
            }
        }
    }

    static public class ValidationTest extends TestCase {
        private final int got;
        private final int expected;

        ValidationTest(int got, int expected) {
            super("Does " + got + " = " + expected);
            this.got = got;
            this.expected = expected;
        }
        @Override
        public void runTest() {
            assertEquals(got, expected);
        }
    }
}
