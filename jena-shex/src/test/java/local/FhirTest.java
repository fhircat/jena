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
package local;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.parser.ShExC;
import org.apache.jena.shex.reporting.ExpressionHReport;
import org.apache.jena.shex.reporting.ValidateOnlyReporter;
import org.apache.jena.shex.sys.ShexLib;
import org.apache.jena.shex.sys.ShexValidatorImpl;
import org.apache.jena.shex.reporting.ShexValidationReport;

import java.util.ArrayList;
import java.util.List;

public class FhirTest {

    public static void main(String[] args) {
        final String DIR = "/home/io/Git/dev/shex/fhir-examples/";
        String schemaFile = "R5Plus/Observation.shex";
        String graphFile = "observation-example-f204-creatinine-with-error.ttl";
        String shapeStr = "file:///home/io/Git/dev/shex/fhir-examples/R5Plus/Observation";
        String focusStr = "http://a.example/validate-me";

        ShexSchema sch = ShExC.parse(DIR + schemaFile);
        Graph graph = RDFDataMgr.loadModel(DIR + graphFile).getGraph();

        List<ExpressionHReport> reports = new ArrayList<>();

        Node shape = ResourceFactory.createResource(shapeStr).asNode();
        Node focus = ResourceFactory.createResource(focusStr).asNode();

        ShexValidatorImpl v = new ShexValidatorImpl(null);
        v.setReporter(ValidateOnlyReporter.factory());
        ShexValidationReport report = v.validate(graph, sch, shape, focus);
        System.out.println("Conforms: " + report.conforms());
        ShexLib.printReport(report);
    }
}

