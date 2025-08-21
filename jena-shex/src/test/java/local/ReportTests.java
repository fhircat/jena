package local;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.parser.ShExC;
import org.apache.jena.shex.reporting.FancyExhaustiveReporter;
import org.apache.jena.shex.reporting.SimpleExhaustiveReporter;
import org.apache.jena.shex.sys.ShexLib;
import org.apache.jena.shex.sys.ShexValidatorImpl;
import org.apache.jena.shex.reporting.ShexValidationReport;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ReportTests {

    static final String DIR = "/home/io/Git/dev/shex/jena/jena-shex/src/test/files/error-report-tests/";
    static String testName = "node-constraint";


    public static void main(String[] args) {
        System.out.println(System.getProperty("user.dir"));

        String schemaFile = testName + ".shex";
        String graphFile = testName + ".ttl";
        String mapFile = testName + "-map.ttl";

        List<Pair<String, String>> map = readMapFile(DIR+mapFile);

        printProblem(schemaFile, graphFile, map);

        runTest(schemaFile, graphFile, map);
    }

    /* Test cases to be improved
    - "node-constraint.shex", "node-constraint.ttl", "http://a.example/n",
        - "http://a.example/P1-V-1" : toString of value range constraint not good
        - in general, the messages for node constraints can be improved, e.g. "http://a.example/P1-IRI" there are repetetions
    -
     */


    public static void runTest(String schemaFile, String graphFile, List<Pair<String, String>> map) {
        ShexSchema sch = ShExC.parse(DIR + schemaFile);
        Graph graph = RDFDataMgr.loadModel(DIR + graphFile).getGraph();

        for (Pair<String, String> p : map) {
            Node focus = ResourceFactory.createResource(p.getLeft()).asNode();
            Node shape = ResourceFactory.createResource(p.getRight()).asNode();

            ShexValidatorImpl v = new ShexValidatorImpl(null);
            v.setReporter(FancyExhaustiveReporter.factory());
            ShexValidationReport report = v.validate(graph, sch, shape, focus);
            System.out.println("Conforms: " + report.conforms());
            ShexLib.printReport(report);
            System.out.println("-----------------------------------------");
        }
    }

    static void printProblem(String schemaFile, String graphFile, List<Pair<String, String>> map) {

        try {

            System.out.println("---- Schema ------");
            for (String l: Files.readAllLines(Paths.get(DIR, schemaFile))) {
                if (l.startsWith("PREFIX") || l.isEmpty()) continue;
                System.out.println(l);
            }
            System.out.println("---- Graph ------");
            for (String l: Files.readAllLines(Paths.get(DIR, graphFile))) {
                if (l.startsWith("PREFIX") || l.isEmpty()) continue;
                System.out.println(l);
            }
            System.out.println("---- Checking------");
            for (Pair<String, String> p: map) {
                System.out.println(p);
            }
            System.out.println("---------------------------------\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static List<Pair<String, String>> readMapFile (String mapFile) {
        List<Pair<String, String>> result = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(mapFile));
            for (String l: lines) {
                if (! l.contains("@")) continue;
                String[] ns = l.split("@");
                String node = ns[0].trim().replace("ex:", "http://a.example/");
                String shape = ns[1].trim().replace("ex:", "http://a.example/")
                        .replace(",","");
                result.add(new Pair<>(node, shape));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


}

