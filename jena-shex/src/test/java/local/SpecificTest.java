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
import org.apache.jena.shex.reporting.SimpleExhaustiveReporter;
import org.apache.jena.shex.reporting.ShexValidationReport;
import org.apache.jena.shex.sys.ShexLib;
import org.apache.jena.shex.sys.ShexValidatorImpl;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.parser.ShExC;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpecificTest {

    public static void main(String[] args) {

        final String DIR = "/home/io/Git/dev/shex/jena/jena-shex/src/test/files/shexTest/";
        String schemaFile = "vitals-RESTRICTS.shex";
        String graphFile = "vitals.ttl";
        List<String> focusShapePairs = new ArrayList<>();
        // Shape
        focusShapePairs.add("http://a.example/#Vital");
        // Focus node
        focusShapePairs.add("http://a.example/#lie");
        focusShapePairs.add("valid");
        /*
        focusShapePairs.add("http://inst.example/Issue2");
        focusShapePairs.add("http://schema.example/IssueShape");
        focusShapePairs.add("valid");
        focusShapePairs.add("http://inst.example/Issue3");
        focusShapePairs.add("http://schema.example/IssueShape");
        focusShapePairs.add("valid");
        */

        ShexSchema sch = ShExC.parse(DIR + "schemas/" + schemaFile);
        Graph graph = RDFDataMgr.loadModel(DIR + "validation/" +  graphFile).getGraph();

        List<ShexValidationReport> reports = new ArrayList<>();

        Iterator<String> it = focusShapePairs.iterator();
        while (it.hasNext()) {
            Node shape = ResourceFactory.createResource(it.next()).asNode();
            Node focus = ResourceFactory.createResource(it.next()).asNode();
            String exp = it.next();

            ShexValidatorImpl v = new ShexValidatorImpl(null);
            v.setReporter(SimpleExhaustiveReporter.factory());
            ShexValidationReport report = v.validate(graph, sch, shape, focus);
            System.out.println("Expected: " + exp + "  Result: " + report.conforms());

            reports.add(report);
        }
        System.out.println("-----------------------------------");
        for (ShexValidationReport report : reports) {
            ShexLib.printReport(report);
            System.out.println("----------------------------------");
        }
    }
}
