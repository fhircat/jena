package org.apache.jena.shex.manifest;

import org.apache.jena.atlas.json.io.JSWriter;
import org.apache.jena.riot.writer.RDFJSONWriter;

import java.io.OutputStream;
import java.io.StringWriter;

public class ManifestWriter {
    public void write(OutputStream os, ValidationManifest manifest) {
        JSWriter out = new JSWriter(os);
        out.startOutput();
//        out.startObject();
//        out.key("entries");
        out.startArray();
        boolean first = true;
        for (ValidationEntry entry: manifest.getEntries()) {
            if (first)
                first = false;
            else
                out.arraySep();
            out.startObject();
            entry.writeJSON(out);
            out.finishObject();
        }
        out.finishArray();
//        out.finishObject();
        out.finishOutput();
    }
}
