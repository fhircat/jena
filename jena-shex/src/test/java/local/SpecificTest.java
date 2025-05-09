package local;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.stream.Locator;
import org.apache.jena.riot.system.stream.StreamManager;
import org.apache.jena.shex.sys.ShexValidatorImpl2;
import org.apache.jena.shex.validation.*;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.parser.ShExC;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpecificTest {




    public static void main(String[] args) {

        final String DIR = "/home/io/Git/dev/shex/jena/jena-shex/src/test/files/shexTest/";
        String schemaFile = "2RefS1-IS2.shex";
        String graphFile = "In1_Ip1_In2.In2_Ip2_LX.ttl";
        List<String> focusShapePairs = new ArrayList<>();
        // Shape
        focusShapePairs.add("http://a.example/S1");
        // Focus node
        focusShapePairs.add("http://a.example/n1");
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

        List<ShexReport> reports = new ArrayList<>();

        Iterator<String> it = focusShapePairs.iterator();
        while (it.hasNext()) {
            Node shape = ResourceFactory.createResource(it.next()).asNode();
            Node focus = ResourceFactory.createResource(it.next()).asNode();
            String exp = it.next();

            ShexValidatorImpl2 v = new ShexValidatorImpl2(null);
            ShexReport report = v.validate(graph, sch, shape, focus);
            System.out.println("Expected: " + exp + "  Result: " + report.conforms());

            reports.add(report);
        }
        System.out.println("-----------------------------------");
        for (ShexReport report : reports)
            System.out.println(report);
    }
}
