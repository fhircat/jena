package org.apache.jena.shex.manifest;

import org.apache.jena.atlas.json.io.JSWriter;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public class ShExMapEntry extends ManifestEntry {

    public static final String KEY_OUTPUT_SCHEMA = "outputSchema";
    private String outputSchema;
    public static final String KEY_OUTPUT_SCHEMA_URL = "outputSchemaURL";
    private URI outputSchemaUrl;

    public static ShExMapEntry newEntry(Map<String, SourcedString> nvps) {
        ShExMapEntry entry = new ShExMapEntry();
        entry.configureState(nvps);
        return entry;
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

    public void writeJSON(JSWriter out) {
        super.writeJSON(out);
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
}
