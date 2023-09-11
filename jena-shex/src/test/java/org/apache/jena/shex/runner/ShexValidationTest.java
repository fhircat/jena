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

package org.apache.jena.shex.runner;

import org.apache.jena.arq.junit.manifest.ManifestEntry;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.json.io.JSONHandler;
import org.apache.jena.atlas.json.io.parser.JSONParser;
import org.apache.jena.atlas.lib.IRILib;
import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.*;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.semact.TestSemanticActionPlugin;
import org.apache.jena.shex.sys.ShexLib;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/** A Shex validation test. Created by {@link RunnerShexValidation}.  */
public class ShexValidationTest implements Runnable {

    private final ManifestEntry entry;
    private final TestType testType;
    private final String schema;
    private final Node shape;
    private final Resource data;
    private final Node focus;
    private final ShexSchema shapes;
    private final String shapeMapURI;
    private final ShapeMap shapeMap;
    private final boolean positiveTest;
    private final ParsedShExReport expectedResult;
    private final boolean verbose = false;
    private final List<Resource> traits;
    private List<Pair<Resource,String>> extensionResults;

    enum TestType{ ShapeFocus, StartFocus, ShapeMap }

    // Expected: ShexT.schema, ShexT.shape, ShexT.data, ShexT.focus
    static Runnable testShexValidationShapeFocus(ManifestEntry entry) {
        Resource action = entry.getAction();
        Resource schema = action.getProperty(ShexT.schema).getResource();
        Resource shape = action.getProperty(ShexT.shape).getResource();
        Resource data = action.getProperty(ShexT.data).getResource();
        // URI or literal.
        RDFNode focus = action.getProperty(ShexT.focus).getObject();
        return new ShexValidationTest(entry,
                                      data, schema, shape, (String)null, focus,
                                      TestType.ShapeFocus);
    }
    // Expected: ShexT.schema (with start), ShexT.data, ShexT.focus
    static Runnable testShexValidationStartFocus(ManifestEntry entry) {
        Resource action = entry.getAction();
        Resource schema = action.getProperty(ShexT.schema).getResource();
        Resource data = action.getProperty(ShexT.data).getResource();
     // URI or literal.
        RDFNode focus = action.getProperty(ShexT.focus).getObject();
        return new ShexValidationTest(entry,
                                      data, schema, (Resource)null, (String)null, focus,
                                      TestType.StartFocus);
    }

    // Expected: ShexT.schema (with start), ShexT.map, ShexT.data
    static Runnable testShexValidationMap(ManifestEntry entry) {
        Resource action = entry.getAction();
        Resource schema = action.getProperty(ShexT.schema).getResource();
        Resource map = action.getProperty(ShexT.map).getResource();
        Resource data = action.getProperty(ShexT.data).getResource();
        return new ShexValidationTest(entry,
                                      data, schema, (Resource)null, map.getURI(), null,
                                      TestType.ShapeMap);
    }

    public ShexValidationTest(ManifestEntry entry,
                              Resource data, Resource schema, Resource shape, String shapeMapRef, RDFNode focus,
                              TestType testType) {
        // For reading data and schema with the same base
        String base = null;
        if ( entry.getEntry().isURIResource() ) {
            String fn = IRILib.IRIToFilename(entry.getEntry().getURI());
            int idx = fn.lastIndexOf('/');
            if ( idx > 0 )
                base = fn.substring(0,idx+1);
            base = IRILib.filenameToIRI(base);
        }

        this.entry = entry;
        this.testType = testType;
        this.schema = schema.getURI();
        this.data  = data;
        this.shape = (shape!=null) ? shape.asNode() : null;
        this.focus = (focus == null) ? null : focus.asNode();
        this.shapeMapURI = shapeMapRef;
        this.shapeMap = (shapeMapRef == null)
                ? null
                : Shex.readShapeMapJson(shapeMapRef);
        this.shapes = Shex.readSchema(schema.getURI(), base);
        this.positiveTest = entry.getTestType().equals(ShexT.cValidationTest);
        Resource expectedResultJson = entry.getResult();
        if (expectedResultJson != null) {
                /*
                List<ReportItem> entries = new ArrayList<>();
                List<ShexRecord> reports = new ArrayList<>();
                PrefixMapping prefixes = new PrefixMappingImpl();
                ShexReport exected = new ShexReport(entries, reports, prefixes);
                 */
            this.expectedResult = new ParsedShExReport();
            try {
                this.expectedResult.readJson(expectedResultJson.getURI(), base);
            } catch (FileNotFoundException e) {
                fail("unable to parse expected results from " + expectedResultJson.getURI());
            }
        } else {
            this.expectedResult = null;
        }
        this.traits = ShexTests.extractTraits(entry);
        this.extensionResults = ShexTests.extractExtensionResults(entry);
    }

    @Override
    public void run() {
        Graph graph = RDFDataMgr.loadGraph(data.getURI());
        try {
            if ( ShexTests.dumpTest )
                describeTest();
            ShexReport report;
            TestSemanticActionPlugin semActPlugin = new TestSemanticActionPlugin();
            List<SemanticActionPlugin> semanticActionPlugins = Collections.singletonList(semActPlugin);
            switch (this.testType) {
                case ShapeFocus :
                    report = ShexValidator.getNew(semanticActionPlugins).validate(graph, shapes, shape, focus);
                    break;
                case ShapeMap :
                    report = ShexValidator.getNew(semanticActionPlugins).validate(graph, shapes, shapeMap);
                    break;
                case StartFocus : {
                    ShapeDecl startShape = shapes.getStart();
                    report = ShexValidator.getNew(semanticActionPlugins).validate(graph, shapes, startShape, focus);
                    break;
                }
                default:
                    throw new InternalErrorException("No test type");
            }

            boolean b = (positiveTest == report.conforms());
            if ( !b ) {
                if ( ! ShexTests.dumpTest )
                    describeTest();
            }
            if (this.expectedResult != null) {
                assertTrue("expected results to match " + this.expectedResult.getSource(), expectedResult.matches(report));
            }
            if (this.extensionResults != null) {
                List<String> output = semActPlugin.getOut();
                assertEquals(String.format("expected %s lines from SemAct, got %s", String.join("\n", extensionResults.stream().map(p -> p.getRight()).collect(Collectors.toList())), String.join("\n", output)), output.size(), extensionResults.size());
                for(int i = 0; i < extensionResults.size(); i++) {
                    String expected = extensionResults.get(i).getRight();
                    String actual = output.get(i);
                    assertTrue("expected: " + expected + ", actual: " + actual, expected.equals(actual));
                }
            }
            assertEquals(entry.getName(), positiveTest, report.conforms());
        } catch (java.lang.AssertionError ex) {
            throw ex;
        } catch (Throwable ex) {
            describeTest();
            System.out.println("Exception: "+ex.getMessage());
            if ( ! ( ex instanceof Error ) )
                ex.printStackTrace(System.out);
            else
                System.out.println(ex.getClass().getName());
            Shex.printSchema(shapes);
            throw ex;
        }
    }

    private void describeTest() {
        System.out.println("** "+ShexTests.fragment(entry));
        System.out.println("Schema:   "+schema);
        System.out.println("Data:     "+data);

        if ( shape != null )
            System.out.println("Shape:    "+ShexLib.displayStr(shape));
        if ( focus != null )
            System.out.println("Focus:    "+ShexLib.displayStr(focus));
        if ( shapeMapURI != null )
            System.out.println("Map:      "+shapeMapURI);

        System.out.println("Positive: "+positiveTest);
        {
            String fn = IRILib.IRIToFilename(schema);
            String s = IO.readWholeFileAsUTF8(fn);
            System.out.println("-- Schema:");
            System.out.print(s);
            if ( ! s.endsWith("\n") )
                System.out.println();
        }
        if ( shapeMapURI != null ) {
            String fn = IRILib.IRIToFilename(shapeMapURI);
            String s = IO.readWholeFileAsUTF8(fn);
            System.out.println("-- Shape map:");
            System.out.print(s);
            if ( ! s.endsWith("\n") )
                System.out.println();
        }
        {
            String dfn = IRILib.IRIToFilename(data.getURI());
            String s = IO.readWholeFileAsUTF8(dfn);
            System.out.println("-- Data:");
            System.out.print(s);
            if ( ! s.endsWith("\n") )
                System.out.println();
            System.out.println("-- --");
        }
        Shex.printSchema(shapes);

        System.out.println("-- --");
    }

    private enum State { // moved this out further and further 'till java stopped whining
        start, node, shapeResList, shapeResObject, shapeResContent, shapeResShape, shapeResRes, done
    };
    static final Map<String, State> keyToState = Map.of
            ("shape", State.shapeResShape,
                    "result", State.shapeResRes);

    public class ParsedShExReport {
        class MyShexRecord {
            final String node;
            final String shape;
            final ShexStatus res;

            public MyShexRecord(String node, String shape, ShexStatus res) {
                this.node = node;
                this.shape = shape;
                this.res = res;
            }
        }
        public String getSource() {
            return source;
        }

        String source = null;
        JSONParser jsonParser = new JSONParser();
        private final List<MyShexRecord> reports;
        void addReport (MyShexRecord record) { reports.add(record); }
        ParsedShExReport ()  {
            reports = new ArrayList<>();
        }
        ParsedShExReport readJson(String url, String base) throws FileNotFoundException {
            this.source = url;
            ReportHandler reportHandler = new ReportHandler(url, this);
            String reportJsonPath = url.substring(7);
            jsonParser.parseAny(new FileReader(reportJsonPath), reportHandler);
            return this;
        }

        public boolean matches(ShexReport report) {
            Iterator<MyShexRecord> myReports = reports.iterator();
            AtomicBoolean res = new AtomicBoolean(true);
            report.forEachReport(resultReport -> {
                if (res.get() == false)
                    return;
                if (!myReports.hasNext()) {
                    res.set(false);
                } else {
                    MyShexRecord mine = myReports.next();
                    if (!resultReport.node.getURI().equals(mine.node)
                        || !resultReport.shapeExprLabel.getURI().equals(mine.shape)
                        || resultReport.status != mine.res)
                        res.set(false);
                }
            });
            if (!res.get())
                return false;
            if (myReports.hasNext())
                return false;
            return true;
        }

        /* modeled on https://github.com/fhircat/jena/blob/refactor-ast_ShExMap-manifest/jena-shex/src/main/java/org/apache/jena/shex/manifest/ManifestReader.java
        why can't I make ReportHandler static?
        {
          "http://example/issue1": [{"shape": "http://schema.example/IssueShape", "result": true}],
          "http://example/issue2": [{"shape": "http://schema.example/IssueShape", "result": false}],
          "http://example/issue3": [{"shape": "http://schema.example/IssueShape", "result": false}]
        }

        start: startParse -> .
        start: startObject -> node
        node: valueString -> . node = image
        node: keyPair -> shapeResList
        shapeResList:startArray -> shapeResObject
        shapeResObject: startObject -> shapeResContent shape = null, res = null
        shapeResContent: valueString -> shapeResShape | shapeResRes
        shapeResShape: valueString -> shapeResContent shape = image
        shapeResRes: valueBoolean -> shapeResContent res = image
        shapeResContent: finishObject -> shapeResObject !shape, !res, push new Result(shape, res)
        shapeResObject: finishArray -> node
        node: finishObject -> done
        done: finishParse -> start

        startParse
          start -> .

        finishParse
          done -> startParse # re-usable

        startObject
          start -> node
          shapeResObject -> shapeResContent shape = null, res = null

        keyPair
          node -> shapeResList

        finishObject
          shapeResContent -> shapeResObject !shape, !res, push new Result(shape, res)
          node -> done

        startArray
          shapeResList -> shapeResObject

        finishArray
          shapeResObject -> node

        valueString
          node -> . node = image
          shapeResContent -> shapeResShape | shapeResRes
          shapeResShape -> shapeResContent shape = image

        valueBoolean
          shapeResRes -> shapeResContent res = image

         */

        private class ReportHandler implements JSONHandler {
            private String base;
            private ParsedShExReport report;

            private String node = null;
            private String shape = null;
            private ShexStatus res = ShexStatus.nonconformant;
            private String lead = "";

            protected void log(String s) {
//                System.out.println(lead + s + " " + state);
            }
            protected void enter(String s) { log(s); lead = lead + "· "; }
            protected void level(String s) { log(s); }
            protected void exit(String s) { lead = lead.substring(0, lead.length() - 2); log(s); }

            public ReportHandler(String base, ParsedShExReport report) {
                this.base = base;
                this.report = report;
            }
            State state = State.start;

            public ParsedShExReport getReport() {
                return report;
            }

            // JSONHandler overrides

            @Override
            public void startParse(long currLine, long currCol) {
                enter("startParse(" + currLine + ", " + currCol + ")");
                assert(state == State.start);
            }

            @Override
            public void finishParse(long currLine, long currCol) {
                exit("finishParse(" + currLine + ", " + currCol + ")");
                state = State.start;
            }

            @Override
            public void startObject(long currLine, long currCol) {
                enter("startObject(" + currLine + ", " + currCol + ")");
                switch (state) {
                    case start: state = State.node; break;
                    case shapeResObject: state = State.shapeResContent; break;
                    default: assert(false);
                }
            }

            @Override
            public void finishObject(long currLine, long currCol) {
                exit("finishObject(" + currLine + ", " + currCol + ")");
                switch (state) {
                    case shapeResContent:
                        state = State.shapeResObject;
//                        System.out.println("<" + this.node + ">@" + (this.res == ShexStatus.conformant ? "" : "!") + "<" + this.shape + ">");
                        this.report.addReport(new MyShexRecord(node, shape, res));
                        this.shape = null;
                        this.res = ShexStatus.nonconformant;
                        break;
                    case node: state = State.done; break;
                    default: assert(false);
                }
            }

            @Override
            public void startPair(long currLine, long currCol) {
                enter("startPair(" + currLine + ", " + currCol + ")");
            }

            @Override
            public void keyPair(long currLine, long currCol) {
                level("keyPair(" + currLine + ", " + currCol + ")");
                switch (state) {
                    case node: state = State.shapeResList; break;
                    case shapeResShape:
                    case shapeResRes:
                        break;
                    default: assert(false);
                }
            }

            @Override
            public void finishPair(long currLine, long currCol) {
                exit("finishPair(" + currLine + ", " + currCol + ")");
            }

            @Override
            public void startArray(long currLine, long currCol) {
                enter("startArray(" + currLine + ", " + currCol + ")");
                switch (state) {
                    case shapeResList: state = State.shapeResObject; break;
                    default: assert(false);
                }
            }

            @Override
            public void element(long currLine, long currCol) {
                level("element(" + currLine + ", " + currCol + ")");
            }

            @Override
            public void finishArray(long currLine, long currCol) {
                exit("finishArray(" + currLine + ", " + currCol + ")");
                switch (state) {
                    case shapeResObject: state = State.node; break;
                    default: assert(false);
                }
            }
            @Override
            public void valueString(String image, long currLine, long currCol) {
                level("valueString(\"" + image + "\", " + currLine + ", " + currCol + ")");
                switch (state) {
                case node: state = State.node; this.node = image; break;
                case shapeResContent:
                    State next = keyToState.get(image);
                    if (next != null)
                        state = next;
                    else
                        assert(false);
                    break;
                case shapeResShape: state = State.shapeResContent; this.shape = image; break;
                default: assert(false);
                }
            }

            @Override
            public void valueInteger(String image, long currLine, long currCol) {
                level("valueInteger(\"" + image + "\", " + currLine + ", " + currCol + ")");
            }

            @Override
            public void valueDouble(String image, long currLine, long currCol) {
                level("valueDouble(\"" + image + "\", " + currLine + ", " + currCol + ")");
            }

            @Override
            public void valueBoolean(boolean b, long currLine, long currCol) {
                level("valueBoolean(" + b + ", " + currLine + ", " + currCol + ")");
                switch (state) {
                case shapeResRes: this.state = State.shapeResContent; this.res = b ? ShexStatus.conformant : ShexStatus.nonconformant; break;
                default: assert(false);
                }
            }

            @Override
            public void valueNull(long currLine, long currCol) {
                level("valueNull(" + currLine + ", " + currCol + ")");
            }

            @Override
            public void valueDecimal(String image, long currLine, long currCol) {
                level("valueDecimal(\"" + image + "\", " + currLine + ", " + currCol + ")");
            }
        }
    }
}
