package org.apache.jena.shex.manifest;

import java.util.Map;
import java.util.Set;

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
    private String schemaUrl;
    private String dataLabel;
    private String data;
    private String dataUrl;
    private String queryMap;
    private String queryMapUrl;
    private String status;

    public void configureState(Map<String,String> nvps) {
        Set<String> keys = nvps.keySet();
        for(String key: keys) {
            switch (key) {
                case KEY_SCHEMA_LABEL:
                    setSchemaLabel(nvps.get(KEY_SCHEMA_LABEL));
                    break;
                case KEY_SCHEMA:
                    setSchema(nvps.get(KEY_SCHEMA));
                    break;
                case KEY_SCHEMA_URL:
                    setSchemaUrl(nvps.get(KEY_SCHEMA_URL));
                    break;
                case KEY_DATA_LABEL:
                    setDataLabel(nvps.get(KEY_DATA_LABEL));
                    break;
                case KEY_DATA:
                    setData(nvps.get(KEY_DATA));
                    break;
                case KEY_DATA_URL:
                    setDataUrl(nvps.get(KEY_DATA_URL));
                    break;
                case KEY_QUERY_MAP:
                    setQueryMap(nvps.get(KEY_QUERY_MAP));
                    break;
                case KEY_QUERY_MAP_URL:
                    setQueryMapUrl(nvps.get(KEY_QUERY_MAP_URL));
                    break;
                case KEY_STATUS:
                    setStatus(nvps.get(KEY_STATUS));
                    break;
            }
        }
    }

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

    public String getSchemaUrl() {
        return schemaUrl;
    }

    public void setSchemaUrl(String schemaUrl) {
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

    public String getDataUrl() {
        return dataUrl;
    }

    public void setDataUrl(String dataUrl) {
        this.dataUrl = dataUrl;
    }

    public String getQueryMap() {
        return queryMap;
    }

    public String getQueryMapUrl() {
        return queryMapUrl;
    }

    public void setQueryMapUrl(String queryMapUrl) {
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
}
