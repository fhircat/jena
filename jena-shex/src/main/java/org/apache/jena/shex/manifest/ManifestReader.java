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

package org.apache.jena.shex.manifest;

import org.apache.jena.atlas.json.io.JSONHandler;
import org.apache.jena.atlas.json.io.parser.JSONParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ManifestReader {
    JSONParser jsonParser = new JSONParser();
    public ManifestReader() {  }

    public ManifestReader read (Path path, Manifest manifest) {
        ManifestHandler manifestHandler = new ManifestHandler(path.getParent().toString(), manifest);
        try {
            jsonParser.parseAny(new FileReader(path.toString()), manifestHandler);
            return this;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public ManifestReader read(InputStream in, String base, Manifest manifest) {
        ManifestHandler manifestHandler = new ManifestHandler(base, manifest);
        jsonParser.parseAny(in, manifestHandler);
        return this;
    }

    private static class ManifestHandler<T extends Manifest> implements JSONHandler {
        private final String base;
        private T manifest;
        private Map<String,SourcedString> curEntryState;
        private String lead = "";

        protected void log(String s) {
//            System.out.println(lead + s);
        }
        protected void enter(String s) { log(s); lead = lead + "· "; }
        protected void level(String s) { log(s); }
        protected void exit(String s) { lead = lead.substring(0, lead.length() - 2); log(s); }

        public ManifestHandler(String base, T manifest) {
            this.base = base;
            this.manifest = manifest;
        }

        private enum State {
            start, entries, list, key, value, done
        };
        State state = State.start;
        String lastString;
        String lastKey;

        public T getManifest() {
            return manifest;
        }

        /**
         * resolve string relative against base and read it;
         * @param path
         * @return contents of resource
         */
        private String readResource (Path path) {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // JSONHandler overrides

        @Override
        public void startParse(long currLine, long currCol) {
            enter("startParse(" + currLine + ", " + currCol + ")");
        }

        @Override
        public void finishParse(long currLine, long currCol) {
            exit("finishParse(" + currLine + ", " + currCol + ")");
        }

        @Override
        public void startObject(long currLine, long currCol) {
            enter("startObject(" + currLine + ", " + currCol + ")");
            switch (state) {
                case list: curEntryState = new HashMap<>(); state = State.key; break;
                default: assert(false);
            }
        }

        @Override
        public void finishObject(long currLine, long currCol) {
            exit("finishObject(" + currLine + ", " + currCol + ")");
            switch (state) {
                case key: manifest.newEntry(curEntryState); state = State.list; break;
                case done: break;
                default: assert(false);
            }
        }

        @Override
        public void startPair(long currLine, long currCol) {
            enter("startPair(" + currLine + ", " + currCol + ")");
        }

        @Override
        public void keyPair(long currLine, long currCol) {
            level("keyPair(" + currLine + ", " + currCol + ")");
            switch (state) {
                case key: lastKey = lastString; state = State.value; break;
                default: assert(false);
            }
        }

        @Override
        public void finishPair(long currLine, long currCol) {
            exit("finishPair(" + currLine + ", " + currCol + ")");
            if (state == State.done) return;
            assert(state == State.value);
            if (lastKey.endsWith("URL")) {
                String label = lastKey.substring(0, lastKey.length() - 3);
                try {
                    Path source = lastString.startsWith("file://")
                            ? Path.of(new URI(lastString).getPath())
                            : Path.of(base, lastString);
                    String fetched = readResource(source);
                    curEntryState.put(label, new SourcedString(source.toUri(), fetched));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            } else {
                curEntryState.put(lastKey, new SourcedString(null, lastString));
            }
            state = State.key;
        }

        @Override
        public void startArray(long currLine, long currCol) {
            enter("startArray(" + currLine + ", " + currCol + ")");
            switch (state) {
                case start: state = State.list; break;
                default: assert(false);
            }
        }

        @Override
        public void element(long currLine, long currCol) {
            level("element(" + currLine + ", " + currCol + ")");
        }

        @Override
        public void finishArray(long currLine, long currCol) {
            exit("finishArray(" + currLine + ", " + currCol + ")");
            state = State.done;
        }

        @Override
        public void valueString(String image, long currLine, long currCol) {
            level("valueString(\"" + image + "\", " + currLine + ", " + currCol + ")");
            lastString = image;
        }

        @Override
        public void valueInteger(String image, long currLine, long currCol) {
            level("valueInteger(\"" + image + "\", " + currLine + ", " + currCol + ")");
        }

        @Override
        public void valueDouble(String image, long currLine, long currCol) {
            level("valueDouble(\"" + image + "\", " + currLine + ", " + currCol + ")");
        }

        @Override
        public void valueBoolean(boolean b, long currLine, long currCol) {
            level("valueBoolean(" + b + ", " + currLine + ", " + currCol + ")");
        }

        @Override
        public void valueNull(long currLine, long currCol) {
            level("valueNull(" + currLine + ", " + currCol + ")");
        }

        @Override
        public void valueDecimal(String image, long currLine, long currCol) {
            level("valueDecimal(\"" + image + "\", " + currLine + ", " + currCol + ")");
        }
    }
}
