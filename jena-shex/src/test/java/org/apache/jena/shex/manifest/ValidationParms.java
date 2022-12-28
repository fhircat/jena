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

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.*;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.sparql.graph.GraphFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

public class ValidationParms {
    public Graph graph;
    public ShexSchema schema;
    public ShapeMap smap;

    public ValidationParms(ManifestEntry manifestEntry, String base) {
        schema = Shex.schemaFromString(manifestEntry.getSchema(), base);

        InputStream dataInputStream = new ByteArrayInputStream(manifestEntry.getData().getBytes());
        graph = GraphFactory.createDefaultGraph();
        RDFDataMgr.read(graph, dataInputStream, base, Lang.TTL);

        InputStream queryMap = new ByteArrayInputStream(manifestEntry.getQueryMap().getBytes());
        smap = Shex.readShapeMap(queryMap, base);
    }

    public ShexReport validate(List<SemanticActionPlugin> semanticActionPlugins) {
        return (semanticActionPlugins == null
                ? ShexValidator.get()
                : ShexValidator.getNew(semanticActionPlugins)).validate(graph, schema, smap);
    }
}
