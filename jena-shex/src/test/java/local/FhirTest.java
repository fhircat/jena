package local;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.parser.ShExC;
import org.apache.jena.shex.sys.ShexValidatorImpl2;
import org.apache.jena.shex.sys.SysShex;
import org.apache.jena.shex.validation.ExhaustiveShexReportElement;
import org.apache.jena.shex.validation.ShexReport;
import org.apache.jena.shex.validation.ShexReportElement;
import org.apache.jena.shex.validation.ValidationContext2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FhirTest {

    public static void main(String[] args) {
        final String DIR = "/home/io/Git/dev/shex/fhir-examples/";
        String schemaFile = "R5Plus/Observation.shex";
        String graphFile = "observation-example-f204-creatinine.ttl";
        String shapeStr = "file:///home/io/Git/dev/shex/fhir-examples/R5Plus/Observation";
        String focusStr = "http://a.example/validate-me";

        ShexSchema sch = ShExC.parse(DIR + schemaFile);
        Graph graph = RDFDataMgr.loadModel(DIR + graphFile).getGraph();

        List<ShexReportElement> reports = new ArrayList<>();

        Node shape = ResourceFactory.createResource(shapeStr).asNode();
        Node focus = ResourceFactory.createResource(focusStr).asNode();

        ShexValidatorImpl2 v = new ShexValidatorImpl2(null);
        ShexReport report = v.validate(graph, sch, shape, focus);
        System.out.println("Conforms: " + report.conforms());
    }
}

