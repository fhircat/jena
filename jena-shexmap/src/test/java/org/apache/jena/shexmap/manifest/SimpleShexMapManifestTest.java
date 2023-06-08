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

import org.apache.jena.shex.ShexReport;
import org.apache.jena.shex.manifest.ManifestEntry;
import org.apache.jena.shex.manifest.yaml.YamlManifestLoader;
import org.apache.jena.shex.manifest.yaml.YamlManifestLoaderTest;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.semact.ShExMapSemanticActionPlugin;
import org.junit.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SimpleShexMapManifestTest {

    static String base = "./src/test/files";
    @Test
    public void singleEntryTest() {
        YamlManifestLoader yamlLoader = new YamlManifestLoader();
        ShExMapManifest manifest = yamlLoader.loadShExMapManifest(YamlManifestLoaderTest.class.getResourceAsStream("/simple-shexmap-manifest.yaml"));
        ManifestEntry entry = manifest.getEntries().get(0);

        // Set up validation parameters
        ValidationParms parms = new ValidationParms(entry, "http://something.com");

        // validate with ShExMap plugin
        ShExMapSemanticActionPlugin semActPlugin = new ShExMapSemanticActionPlugin(parms.schema);
        List<SemanticActionPlugin> semanticActionPlugins = Collections.singletonList(semActPlugin);
        ShexReport report = parms.validate(semanticActionPlugins);

        // Nothing works if it doesn't pass validation
        assertTrue(report.conforms());

        List<String> output = semActPlugin.getOut();
        System.out.println(String.join("\n", output));
        ShExMapSemanticActionPlugin.BindingNode tree = semActPlugin.getBindingTree();
        if (tree != null)
            semActPlugin.getBindingTreeAsJson(System.out);
    }
}
