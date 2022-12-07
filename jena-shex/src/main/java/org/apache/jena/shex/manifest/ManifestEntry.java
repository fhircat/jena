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

import org.apache.jena.atlas.json.io.JSWriter;

import java.util.*;
import java.net.URI;

public abstract class ManifestEntry {

    public static final String KEY_SCHEMA_LABEL = "schemaLabel";
    public static final String KEY_SCHEMA = "schema";
    public static final String KEY_SCHEMA_URL = "schemaURL";
    public static final String KEY_DATA_LABEL = "dataLabel";
    public static final String KEY_DATA = "data";
    public static final String KEY_DATA_URL = "dataURL";
    public static final String KEY_QUERY_MAP = "queryMap";
    public static final String KEY_QUERY_MAP_URL = "queryMapURL";
    public static final String KEY_STATUS = "status";

    private String schemaLabel;
    private String schema;
    private URI schemaUrl;
    private String dataLabel;
    private String data;
    private URI dataUrl;
    private String queryMap;
    private URI queryMapUrl;
    private String status;

    public String getSchemaLabel() {
        return schemaLabel;
    }

    public void setSchemaLabel(String schemaLabel) {
        this.schemaLabel = schemaLabel;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public URI getSchemaUrl() {
        return schemaUrl;
    }

    public void setSchemaUrl(URI schemaUrl) {
        this.schemaUrl = schemaUrl;
    }

    public String getDataLabel() {
        return dataLabel;
    }

    public void setDataLabel(String dataLabel) {
        this.dataLabel = dataLabel;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public URI getDataUrl() {
        return dataUrl;
    }

    public void setDataUrl(URI dataUrl) {
        this.dataUrl = dataUrl;
    }

    public String getQueryMap() {
        return queryMap;
    }

    public URI getQueryMapUrl() {
        return queryMapUrl;
    }

    public void setQueryMapUrl(URI queryMapUrl) {
        this.queryMapUrl = queryMapUrl;
    }

    public void setQueryMap(String queryMap) {
        this.queryMap = queryMap;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ManifestEntry(Map<String, SourcedString> nvps) {
        List<String> matched = new ArrayList<>();
        for(String key: nvps.keySet()) {
            boolean found = true;
            String value = nvps.get(key).getValue();
            URI source = nvps.get(key).getSource();
            found = setProperty(key, value, source);
            if (found)
                matched.add(key);
        }
        for (String key: matched)
            nvps.remove(key);
    }

    public boolean setProperty(String key, String value, URI source) {
        boolean found = true;
        switch (key) {
            case KEY_SCHEMA_LABEL:
                setSchemaLabel(value);
                break;
            case KEY_SCHEMA:
                setSchema(value);
                setSchemaUrl(source);
                break;
            case KEY_DATA_LABEL:
                setDataLabel(value);
                break;
            case KEY_DATA:
                setData(value);
                setDataUrl(source);
                break;
            case KEY_QUERY_MAP:
                setQueryMap(value);
                setQueryMapUrl(source);
                break;
            case KEY_STATUS:
                setStatus(value);
                break;
            default:
                found = false;
        }
        return found;
    }

    public void writeJson(JSWriter out) {

        if (schemaLabel != null) {
            out.pair(KEY_SCHEMA_LABEL, schemaLabel);
        }

        if (schemaUrl != null) {
            out.pair(KEY_SCHEMA_URL, schemaUrl.toString());
        } else if (schema != null) {
            out.pair(KEY_SCHEMA, schema);
        }

        if (dataLabel != null) {
            out.pair(KEY_DATA_LABEL, dataLabel);
        }

        if (dataUrl != null) {
            out.pair(KEY_DATA_URL, dataUrl.toString());
        } else if (data != null) {
            out.pair(KEY_DATA, data);
        }

        if (queryMapUrl != null) {
            out.pair(KEY_QUERY_MAP_URL, queryMapUrl.toString());
        } else if (queryMap != null) {
            out.pair(KEY_QUERY_MAP, queryMap);
        }

        if (status != null) {
            out.pair(KEY_STATUS, status);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ManifestEntry)) return false;
        ManifestEntry that = (ManifestEntry) o;
        return Objects.equals(getSchemaLabel(), that.getSchemaLabel()) && Objects.equals(getSchema(), that.getSchema()) && Objects.equals(getSchemaUrl(), that.getSchemaUrl()) && Objects.equals(getDataLabel(), that.getDataLabel()) && Objects.equals(getData(), that.getData()) && Objects.equals(getDataUrl(), that.getDataUrl()) && Objects.equals(getQueryMap(), that.getQueryMap()) && Objects.equals(getQueryMapUrl(), that.getQueryMapUrl()) && Objects.equals(getStatus(), that.getStatus());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSchemaLabel(), getSchema(), getSchemaUrl(), getDataLabel(), getData(), getDataUrl(), getQueryMap(), getQueryMapUrl(), getStatus());
    }
}
