package local;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.reporting.ExhaustiveReporter;
import org.apache.jena.shex.reporting.ShexValidationReport;
import org.apache.jena.shex.sys.ShexValidatorImpl;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.parser.ShExC;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpecificTest {

    public static void main(String[] args) {

        final String DIR = "/home/io/Git/dev/shex/jena/jena-shex/src/test/files/shexTest/";
        String schemaFile = "0.shex";
        String graphFile = "empty.ttl";
        List<String> focusShapePairs = new ArrayList<>();
        // Shape
        focusShapePairs.add("http://a.example/S1");
        // Focus node
        focusShapePairs.add("http://a.example/dummy");
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
            v.setReporter(ExhaustiveReporter.factory());
            ShexValidationReport report = v.validate(graph, sch, shape, focus);
            System.out.println("Expected: " + exp + "  Result: " + report.conforms());

            reports.add(report);
        }
        System.out.println("-----------------------------------");
        for (ShexValidationReport report : reports)
            System.out.println(report);
    }
}
