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

package shex.examples;

import org.apache.jena.atlas.logging.LogCtl;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.*;
import org.apache.jena.shex.parser.ParserShExC;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.semact.TestSemanticActionPlugin;
import org.apache.jena.shex.sys.ShexLib;

import java.util.Collections;
import java.util.List;

/** Validate based on building a shape map in the code. */
public class Shex04_executeSemAct {
    static { LogCtl.setLogging(); }

    public static void main(String ...args) {
        String SHAPES = "examples/Issue-schema-log.shex";
        String SHAPES_MAP = "examples/Issue1.smap";
        String DATA = "examples/Issue1.ttl";

        System.out.println("Read data");
        Graph dataGraph = RDFDataMgr.loadGraph(DATA);

        System.out.println("Read shapes");
//        ParserShExC.DEBUG_PARSE = true;
        ShexSchema shapes = Shex.readSchema(SHAPES);

        // Shapes map.
        System.out.println("Read shapes map");
        ShapeMap shapeMap = Shex.readShapeMap(SHAPES_MAP);

        Node data1 = NodeFactory.createURI("http://base.example/#Issue1");

        System.out.println();
        System.out.println("Validate 1");
        TestSemanticActionPlugin semActPlugin = new TestSemanticActionPlugin();
        List<SemanticActionPlugin> semanticActionPlugins = Collections.singletonList(semActPlugin);
        ShexReport report1 = ShexValidator.getNew(semanticActionPlugins).validate(dataGraph, shapes, shapeMap, data1);
        System.out.println("report:");
        ShexLib.printReport(report1);
        List<String> output = semActPlugin.getOut();
        System.out.println("semact output:");
        System.out.println(String.join("\n", output));
    }
}
