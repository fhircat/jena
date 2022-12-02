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

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.jena.atlas.json.io.JSONHandler;
import org.apache.jena.atlas.json.io.parser.JSONParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class TestShExMapManifest {

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        String base = "./src/test/files";
        Manifest manifest = new ShExMapManifestReader().read(Path.of(base, "ShExMap-manifest.json"));
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
            super(Integer.toString(got) + ":" + Integer.toString(expected));
            this.got = got;
            this.expected = expected;
        }
        @Override
        public void runTest() {
            // define assertion here  <===
            assertEquals(got, expected);
        }
    }

    static public class Manifest {
        List<Entry> entries = new ArrayList<>();

        public Entry newEntry () {
            Entry ret = new Entry();
            entries.add(ret);
            return ret;
        }

        public class Entry {
            public String schemaLabel;
            public String schema;
            public String dataLabel;
            public String data;
            public String queryMap;
            public String status;
        }
    }

    private static class ShExMapManifestReader {
        JSONParser jsonParser = new JSONParser();
        public ShExMapManifestReader() {
        }
        public Manifest read (Path path) {
            ManifestHandler manifestHandler = new ManifestHandler(path.getParent().toString());
            try {
                Reader manifestReader = new FileReader(path.toString());
                jsonParser.parse(manifestReader, manifestHandler);
                return manifestHandler.getManifest();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        private static class ManifestHandler implements JSONHandler {
            private final String base;
            Manifest manifest = new Manifest();
            Manifest.Entry curEntry;

            public ManifestHandler(String base) {
                this.base = base;
            }

            private static enum State {
                start, x, list, key, value, done
            };
            State state = State.start;
            String lastString;
            String lastKey;

            public Manifest getManifest() {
                return manifest;
            }

            /**
             * resolve string relative against base and read it;
             * @param rel
             * @return
             */
            private String readResource (String rel) {
                try {
                    return Files.readString(Path.of(base, rel), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            // JSONHandler overrides

            @Override
            public void startParse(long currLine, long currCol) {
                // System.out.println("startParse" + currLine + ":" + currCol);
            }

            @Override
            public void finishParse(long currLine, long currCol) {
                // System.out.println("finishParse" + currLine + ":" + currCol);
            }

            @Override
            public void startObject(long currLine, long currCol) {
                // System.out.println("startObject" + currLine + ":" + currCol);
                switch (state) {
                    case start: state = State.x; break;
                    case list: Manifest.Entry entry = manifest.newEntry(); curEntry = entry; state = State.key; break;
                    default: assert(false);
                }
            }

            @Override
            public void finishObject(long currLine, long currCol) {
                // System.out.println("finishObject" + currLine + ":" + currCol);
                switch (state) {
                    case key: state = State.list; break;
                    case done: break;
                    default: assert(false);
                }
            }

            @Override
            public void startPair(long currLine, long currCol) {
                // System.out.println("startPair" + currLine + ":" + currCol);
            }

            @Override
            public void keyPair(long currLine, long currCol) {
                // System.out.println("keyPair" + currLine + ":" + currCol);
                switch (state) {
                    case x: assert(lastString.equals("x")); state = State.list; break;
                    case start: break;
                    case list: throw new Error("");
                    case key: lastKey = lastString; state = State.value; break;
                }
            }

            @Override
            public void finishPair(long currLine, long currCol) {
                // System.out.println("finishPair" + currLine + ":" + currCol);
                if (state == State.done) return;
                assert(state == State.value);
                switch (lastKey) {
                    case "schemaLabel": curEntry.schemaLabel = lastString; break;
                    case "schema": curEntry.schema = lastString; break;
                    case "schemaURL": curEntry.schema = readResource(lastString); break;
                    case "dataLabel": curEntry.dataLabel = lastString; break;
                    case "data": curEntry.data = lastString; break;
                    case "dataURL": curEntry.data = readResource(lastString); break;
                    case "queryMap": curEntry.queryMap = lastString; break;
                    case "queryMapURL": curEntry.queryMap = readResource(lastString); break;
                    case "status": curEntry.status = lastString; break;
                }
                state = State.key;
            }

            @Override
            public void startArray(long currLine, long currCol) {
                // System.out.println("startArray" + currLine + ":" + currCol);
                assert(state == State.list);
            }

            @Override
            public void element(long currLine, long currCol) {
                // System.out.println("element" + currLine + ":" + currCol);
            }

            @Override
            public void finishArray(long currLine, long currCol) {
                // System.out.println("finishArray" + currLine + ":" + currCol);
                state = State.done;
            }

            @Override
            public void valueString(String image, long currLine, long currCol) {
                // System.out.println("valueString" + image + ":" + currLine + ":" + currCol);
                lastString = image;
            }

            @Override
            public void valueInteger(String image, long currLine, long currCol) {
                // System.out.println("valueInteger" + image + ":" + currLine + ":" + currCol);
            }

            @Override
            public void valueDouble(String image, long currLine, long currCol) {
                // System.out.println("valueDouble" + image + ":" + currLine + ":" + currCol);
            }

            @Override
            public void valueBoolean(boolean b, long currLine, long currCol) {
                // System.out.println("valueBoolean" + b + ":" + currLine + ":" + currCol);
            }

            @Override
            public void valueNull(long currLine, long currCol) {
                // System.out.println("valueNull" + currLine + ":" + currCol);
            }

            @Override
            public void valueDecimal(String image, long currLine, long currCol) {
                // System.out.println("valueDecimal" + image + ":" + currLine + ":" + currCol);
            }
        }
    }
}
