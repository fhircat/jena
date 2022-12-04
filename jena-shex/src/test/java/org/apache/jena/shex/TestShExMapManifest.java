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
import org.apache.jena.shex.manifest.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TestShExMapManifest {
    static String base = "./src/test/files";

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new SmallParsedVsManual());

        ValidationManifest manifest = new ValidationManifest();
        new ManifestReader().read(Path.of(base, "Validation-manifest.json"), manifest);
        OutputStream out = new ByteArrayOutputStream();
        new ManifestWriter().write(out, manifest);
        System.out.println(out.toString());

        suite.addTest(new ShExMapTest(1, 1));
        suite.addTest(new ShExMapTest(2, 2));
//        suite.addTest(new ShExMapTest(2, 1)); // huh, for some reason, 2 != 1
        suite.addTest(new ShExMapTest(1, 1));
        return suite;
    }


    static public class SmallParsedVsManual extends TestCase {
        SmallParsedVsManual() { super("SmallParsedVsManual"); }
        public void runTest() {
            ValidationManifest parsed = new ValidationManifest();
            new ManifestReader().read(Path.of(base, "small-manifest.json"), parsed);
            ValidationManifest manual = new ValidationManifest();
            Map<String, SourcedString> nvps = new HashMap<>();
            nvps.put(ValidationEntry.KEY_SCHEMA_LABEL, new SourcedString(null, "clinical observation all refs"));
            manual.addEntry(ValidationEntry.newEntry(nvps));
            assertEquals(parsed, manual);
        }
    }

    static public class Heterogenous extends TestCase {
        Heterogenous() { super("Heterogenous"); }
        public void runTest() {
            Manifest<ManifestEntry> manifest = new Manifest<ManifestEntry>() {
                @Override
                public ManifestEntry newEntry(Map<String, SourcedString> nvps) {
                    ManifestEntry newEntry;
                    if (nvps.containsKey(ShExMapEntry.KEY_OUTPUT_SCHEMA))
                        newEntry = ShExMapEntry.newEntry(nvps);
                    else
                        newEntry = ValidationEntry.newEntry(nvps);
                    addEntry(newEntry);
                    return newEntry;
                }
            };
            new ManifestReader().read(Path.of(base, "ShExMap-manifest.json"), manifest);
            List<ManifestEntry> entries = manifest.getEntries();
            assertEquals(entries.size(), 2);
            assertEquals(entries.get(0).getClass().getName(), "ValidationEntry");
            assertEquals(entries.get(1).getClass().getName(), "ShExMapEntry");

            OutputStream out = new ByteArrayOutputStream();
            new ManifestWriter().write(out, manifest);
            System.out.println(out.toString());
        }
    }

    static public class ShExMapTest extends TestCase {
        private final int got;
        private final int expected;

        ShExMapTest(int got, int expected) {
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
