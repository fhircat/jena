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
package org.apache.jena.shex.manifest.yaml;

import org.apache.jena.shex.manifest.ValidationManifest;
import org.apache.jena.shexmap.manifest.ShExMapManifest;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.*;

public class YamlManifestLoaderTest {

    @Test
    public void loadValidationManifest() {
        YamlManifestLoader yamlLoader = new YamlManifestLoader();
        ValidationManifest manifest = yamlLoader.loadValidationManifest(YamlManifestLoaderTest.class.getResourceAsStream("/shex-manifest.yaml"));
        assertEquals(7, manifest.getEntries().size());
    }

    @Test
    public void loadShExMapManifest() {
        YamlManifestLoader yamlLoader = new YamlManifestLoader();
        ShExMapManifest manifest = yamlLoader.loadShExMapManifest(YamlManifestLoaderTest.class.getResourceAsStream("/shexmap-manifest.yaml"));
        assertEquals(9, manifest.getEntries().size());
    }

    @Test
    public void loadShExMapTestManifest() {
        Yaml yaml = new Yaml(new ShExMapTestManifestConstructor());
        ShExMapManifest manifest = yaml.load(YamlManifestLoaderTest.class.getResourceAsStream("/shexmap-test-manifest.yaml"));
        assertEquals(9, manifest.getEntries().size());
    }
}
