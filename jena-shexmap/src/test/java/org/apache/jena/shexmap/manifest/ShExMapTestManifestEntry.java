/**
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 **/
package org.apache.jena.shexmap.manifest;

import org.apache.jena.atlas.json.io.JSWriter;
import org.apache.jena.shex.manifest.SourcedString;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

public class ShExMapTestManifestEntry extends ShExMapManifestEntry {

    public static final String KEY_EXPECTED_DATA_BINDING_URL = "expectedDataBindingUrl";
    private URI expectedBindingsUrl;
    public static final String KEY_OUTPUT_DATA_URL = "outputDataUrl";
    private URI outputDataUrl;

    public ShExMapTestManifestEntry(Map<String, SourcedString> nvps) {
        super(nvps);
    }

    public boolean setProperty(String key, String value, URI source) {
        boolean found = super.setProperty(key, value, source);
        if (!found) {
            switch (key) {
                case KEY_EXPECTED_DATA_BINDING_URL:
                    setExpectedBindingsUrl(source);
                    break;
                case KEY_OUTPUT_DATA_URL:
                    setOutputDataUrl(source);
                    break;
            }
        }
        return found;
    }

    public void writeJson(JSWriter out) {
        super.writeJson(out);
        if (expectedBindingsUrl != null) {
            out.pair(KEY_EXPECTED_DATA_BINDING_URL, expectedBindingsUrl.toString());
        } else if (outputDataUrl != null) {
            out.pair(KEY_OUTPUT_DATA_URL, outputDataUrl.toString());
        }
    }

    public URI getExpectedBindingsUrl() {
        return expectedBindingsUrl;
    }

    public void setExpectedBindingsUrl(URI expectedBindingsUrl) {
        this.expectedBindingsUrl = expectedBindingsUrl;
    }

    public URI getOutputDataUrl() {
        return outputDataUrl;
    }

    public void setOutputDataUrl(URI outputDataUrl) {
        this.outputDataUrl = outputDataUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShExMapTestManifestEntry)) return false;
        if (!super.equals(o)) return false;
        ShExMapTestManifestEntry that = (ShExMapTestManifestEntry) o;
        return Objects.equals(getExpectedBindingsUrl(), that.getExpectedBindingsUrl()) && Objects.equals(getOutputDataUrl(), that.getOutputDataUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getExpectedBindingsUrl(), getOutputDataUrl());
    }


}
