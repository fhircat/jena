package local;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.parser.ShExC;
import org.apache.jena.shex.sys.ShexValidatorImpl2;
import org.apache.jena.shex.validation.ShexReport;
import org.apache.jena.shex.validation.ShexReportElement;

import java.util.ArrayList;
import java.util.List;

public class ReportTests {

    public static void runTest(String schemaFile, String graphFile, String shapeStr, String focusStr) {
        final String DIR = "/home/io/Git/dev/shex/error-report-examples/";

        ShexSchema sch = ShExC.parse(DIR + schemaFile);
        Graph graph = RDFDataMgr.loadModel(DIR + graphFile).getGraph();

        List<ShexReportElement> reports = new ArrayList<>();

        Node shape = ResourceFactory.createResource(shapeStr).asNode();
        Node focus = ResourceFactory.createResource(focusStr).asNode();

        ShexValidatorImpl2 v = new ShexValidatorImpl2(null);
        ShexReport report = v.validate(graph, sch, shape, focus);
        System.out.println("Conforms: " + report.conforms());
    }

    public static void main(String[] args) {
        runTest(
                /* schema file */ "",
                /* graph file */ "",
                /* shape */ "",
                /* node */ ""
        );
    }
}

