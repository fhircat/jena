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
import org.apache.jena.shex.manifest.ManifestReader;
import org.apache.jena.shex.manifest.Manifest;

import java.nio.file.Path;

public final class TestShExMapManifest {

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        String base = "./src/test/files";
        Path manifestPath = Path.of("./src/test/files", "ShExMap-manifest.json");
        int i = ManifestReader.getI();
        Manifest manifest = ManifestReader.test(manifestPath);// new ManifestReader().read(Path.of(base, "ShExMap-manifest.json"));
        System.out.println(manifest);
        suite.addTest(new ShExMapTest(1, 1));
        suite.addTest(new ShExMapTest(2, 2));
        suite.addTest(new ShExMapTest(2, 1)); // huh, for some reason, 2 != 1
        suite.addTest(new ShExMapTest(1, 1));
        return suite;
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
