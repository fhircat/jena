package local;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.parser.ShExC;
import org.apache.jena.shex.validation.ExhaustiveShexReportElement;
import org.apache.jena.shex.validation.ShexReportElement;
import org.apache.jena.shex.validation.ValidationContext2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FhirTest {

    public static void main(String[] args) {
        final String DIR = "/home/io/Git/dev/shex/fhir-examples";
        String schemaFile = "2RefS1-IS2.shex";
        String graphFile = "observation-example-f204-creatinine.ttl";
        String shapeStr = "Observation";
        String focusStr = "http://a.example/validate-me";

        ShexSchema sch = ShExC.parse(DIR + "schemas/" + schemaFile);
        Graph graph = RDFDataMgr.loadModel(DIR + "validation/" +  graphFile).getGraph();

        List<ShexReportElement> reports = new ArrayList<>();

        Node shape = ResourceFactory.createResource(shapeStr).asNode();
        Node focus = ResourceFactory.createResource(focusStr).asNode();

        ValidationContext2 vCxt = new ValidationContext2(sch, graph,false, false, null);
        ShexReportElement factory = ExhaustiveShexReportElement.factory();

        ShexReportElement report = vCxt.validate(focus, ShapeExprRef.create(shape), factory);
        System.out.println("Result: " + report.getStatus());

        reports.add(report);
    }
}

