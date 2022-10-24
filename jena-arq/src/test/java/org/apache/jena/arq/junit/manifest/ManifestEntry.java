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

package org.apache.jena.arq.junit.manifest;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.TestManifest;

import java.util.ArrayList;
import java.util.List;

public class ManifestEntry {
    private final Manifest manifest;
    private final Resource entry;
    private final String name;
    private final Resource testType;
    private final Resource action;
    private final Resource extensionResults;
    private final Resource result;

    private ManifestEntry(Manifest manifest, Resource entry, String name, Resource testType, Resource action, Resource result) {
        this(manifest, entry, name, testType, action, null, result);
    }

    public ManifestEntry(Manifest manifest, Resource entry, String name, Resource testType, Resource action, Resource extensionResults, Resource result) {
        super();
        this.manifest = manifest;
        this.entry = entry;
        this.name = name;
        this.testType = testType;
        this.action = action;
        this.extensionResults = extensionResults;
        this.result = result;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public Resource getEntry() {
        return entry;
    }

    public String getURI() {
        return entry.getURI();
    }

    public String getName() {
        return name;
    }

    public Resource getTestType() {
        return testType;
    }

    public Resource getAction() {
        return action;
    }

    public Resource getExtensionResults() {
        return extensionResults;
    }

    public Resource getResult() {
        return result;
    }

    public List<Pair<Resource, String>> extractExtensionResults() {
        Resource extensionResults = getExtensionResults();
        if (extensionResults == null)
            return null;
        List<Pair<Resource, String>> pairs = new ArrayList<>();
        StmtIterator listIter = getEntry().listProperties(TestManifest.extensionResults);
        while (listIter.hasNext()) {
            //List head
            Resource listItem = listIter.nextStatement().getResource();
            while (!listItem.equals(RDF.nil)) {
                Resource extensionResult = listItem.getRequiredProperty(RDF.first).getResource(); //TODO Eric, please review. Hopefully this does the trick
                Resource extension = extensionResult.getProperty(TestManifest.extension).getResource();
                Literal prints = extensionResult.getProperty(TestManifest.prints).getLiteral();
                String printStr = prints.getString() ;
                Pair<Resource, String> pair = new Pair<>(extension, printStr);
                pairs.add(pair);
                // Move to next list item
                listItem = listItem.getRequiredProperty(RDF.rest).getResource();
            }
        }
        listIter.close();
        return pairs;
    }
}

