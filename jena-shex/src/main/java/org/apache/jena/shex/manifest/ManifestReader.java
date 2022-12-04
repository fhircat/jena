package org.apache.jena.shex.manifest;

import org.apache.jena.atlas.json.io.JSONHandler;
import org.apache.jena.atlas.json.io.parser.JSONParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
            jsonParser.parse(new FileReader(path.toString()), manifestHandler);
            return this;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ManifestHandler<T extends Manifest> implements JSONHandler {
        private final String base;
        private T manifest;
        ManifestEntry curEntry;
        private Map<String,String> curEntryState;
        private String lead = "";

        protected void log(String s) { System.out.println(lead + s); }
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
                case start: state = State.entries; break;
                case list: curEntryState = new HashMap<>(); state = State.key; break;
                default: assert(false);
            }
        }

        @Override
        public void finishObject(long currLine, long currCol) {
            exit("finishObject(" + currLine + ", " + currCol + ")");
            curEntry = manifest.newEntry(curEntryState);
            switch (state) {
                case key: state = State.list; break;
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
                case entries: assert(lastString.equals("entries")); state = State.list; break;
                case start: break;
                case list: throw new Error("");
                case key: lastKey = lastString; state = State.value; break;
            }
        }

        @Override
        public void finishPair(long currLine, long currCol) {
            exit("finishPair(" + currLine + ", " + currCol + ")");
            if (state == State.done) return;
            assert(state == State.value);
            curEntryState.put(lastKey, lastString);
            state = State.key;
        }

        @Override
        public void startArray(long currLine, long currCol) {
            enter("startArray(" + currLine + ", " + currCol + ")");
            assert(state == State.list);
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
