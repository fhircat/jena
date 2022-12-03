package org.apache.jena.shex.manifest;

import org.apache.jena.atlas.json.io.JSONHandler;
import org.apache.jena.atlas.json.io.parser.JSONParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ManifestReader {
    public static Manifest test (Path path) {
        ManifestReader mr = new ManifestReader();
        Manifest red = mr.read(path);
        System.out.println(red);
        return red;
    }
    JSONParser jsonParser = new JSONParser();
    public ManifestReader() {
    }

    public static int getI() {
        return 999;
    }

    public Manifest read (Path path) {
        ManifestHandler manifestHandler = new ManifestHandler(path.getParent().toString());
        try {
            jsonParser.parse(new FileReader(path.toString()), manifestHandler);
            return manifestHandler.getManifest();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ManifestHandler implements JSONHandler {
        private final String base;
        Manifest manifest = new Manifest();
        ValidationEntry curEntry;

        public ManifestHandler(String base) {
            this.base = base;
        }

        private enum State {
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
                case list: ValidationEntry entry = manifest.newEntry(); curEntry = entry; state = State.key; break;
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
