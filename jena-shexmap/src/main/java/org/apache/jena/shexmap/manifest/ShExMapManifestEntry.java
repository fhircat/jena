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

package org.apache.jena.shexmap.manifest;

import org.apache.jena.atlas.json.io.JSWriter;
import org.apache.jena.shex.manifest.ManifestEntry;
import org.apache.jena.shex.manifest.SourcedString;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

public class ShExMapManifestEntry extends ManifestEntry {

    public static final String KEY_OUTPUT_SCHEMA = "outputSchema";
    private String outputSchema;
    public static final String KEY_OUTPUT_SCHEMA_URL = "outputSchemaURL";
    private URI outputSchemaUrl;

    public static final String KEY_OUTPUT_QUERY_MAP = "outputQueryMap";

    private String outputQueryMap;

    public static final String KEY_STATIC_VARS = "staticVars";

    private Map<String,Object> staticVars;

    public ShExMapManifestEntry(Map<String, SourcedString> nvps) {
        super(nvps);
    }

    public boolean setProperty(String key, String value, URI source) {
        boolean found = super.setProperty(key, value, source);
        if (!found) {
            switch (key) {
                case KEY_OUTPUT_SCHEMA:
                    setOutputSchema(value);
                    setOutputSchemaUrl(source);
                    break;
                case KEY_OUTPUT_QUERY_MAP:
                    setOutputQueryMap(value);
                    break;
            }
        }
        return found;
    }

    public void writeJson(JSWriter out) {
        super.writeJson(out);
        if (outputSchemaUrl != null) {
            out.pair(KEY_OUTPUT_SCHEMA_URL, outputSchemaUrl.toString());
        } else if (outputSchema != null) {
            out.pair(KEY_OUTPUT_SCHEMA, outputSchema);
        } else if(outputQueryMap != null) {
            out.pair(KEY_OUTPUT_QUERY_MAP, outputQueryMap);
        } else if(staticVars != null) {
            staticVars.forEach((name,value) -> {
                out.pair(name, "" + value);//TODO Eric please take a look. Currently making all String types.
            });
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

    public Map<String, Object> getStaticVars() {
        return staticVars;
    }

    public void setStaticVars(Map<String, Object> staticVars) {
        this.staticVars = staticVars;
    }

    public String getOutputQueryMap() {
        return outputQueryMap;
    }

    public void setOutputQueryMap(String outputQueryMap) {
        this.outputQueryMap = outputQueryMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShExMapManifestEntry)) return false;
        if (!super.equals(o)) return false;
        ShExMapManifestEntry that = (ShExMapManifestEntry) o;
        return Objects.equals(getOutputSchema(), that.getOutputSchema()) && Objects.equals(getOutputSchemaUrl(), that.getOutputSchemaUrl()) && Objects.equals(getOutputQueryMap(), that.getOutputQueryMap()) && Objects.equals(getStaticVars(), that.getStaticVars());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getOutputSchema(), getOutputSchemaUrl(), getOutputQueryMap(), getStaticVars());
    }
}
