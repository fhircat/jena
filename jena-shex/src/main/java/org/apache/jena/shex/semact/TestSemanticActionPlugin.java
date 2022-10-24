package org.apache.jena.shex.semact;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.SemAct;
import org.apache.jena.shex.expressions.ShapeExpression;
import org.apache.jena.shex.expressions.TripleExpression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

interface ExtractVar {
    String run (String str);
}

public class TestSemanticActionPlugin implements SemanticActionPlugin {
    static String SemActIri = "http://shex.io/extensions/Test/";
    static Pattern ParsePattern = Pattern.compile("^\\s*([a-zA-Z]+)\\s*\\(([a-zA-Z]+|\\\"(?:(?:[^\\\\\\\"]|\\\\[^\\\"])+)\\\")\\)\\s*$");

    @Override
    public List<String> getUris() {
        List<String> uris = new ArrayList<>();
        uris.add(SemActIri);
        return uris;
    }

    List<String> out = new ArrayList<>();

    public List<String> getOut () { return out; }

    @Override
    public boolean evaluateStart(SemAct semAct, ShexSchema schema) {
        return parse(semAct, (str) -> resolveStartVar(str));
    }

    @Override
    public boolean evaluateShapeExpr(SemAct semAct, ShapeExpression shapeExpression, Node focus) {
        return parse(semAct, (str) -> resolveNodeVar(str, focus));
    }

    @Override
    public boolean evaluateTripleExpr(SemAct semAct, TripleExpression tripleExpression, Collection<Triple> triples) {
        Triple triple = triples.iterator().next(); // should be one triple, as currently defined.
        return parse(semAct, (str) -> resolveTripleVar(str, triple));
    }

    private boolean parse(SemAct semAct, ExtractVar extractor) {
        String code = semAct.getCode();
        if (code == null)
            return true;
        Matcher m = ParsePattern.matcher(code);
        boolean passed = false;
        if (m.find()) {
            String fStr = m.group(1);

            switch (fStr) {
                case "print":
                    out.add(extractor.run(m.group(2)));
                    passed = true;
                case "fail":
                    // do something with the argument?
                    break;
                default:
                    throw new RuntimeException(String.format("%s semantic action function %s was not 'print', or 'fail'", SemActIri, fStr));
            }
        } else {
            throw new RuntimeException(String.format("%s semantic action %s did not match %s", SemActIri, code, ParsePattern));
        }
        return passed;
    }

    private static String resolveStartVar(String varName) {
        if (varName.charAt(0) == '"')
            return varName.replaceAll("\\\\(.)", "$1");

        throw new RuntimeException(String.format("%s semantic action argument %s was not a literal", SemActIri, varName));
    }

    private static String resolveNodeVar(String varName, Node focus) {
        if (varName.charAt(0) == '"')
            return varName.replaceAll("\\\\(.)", "$1");

        Node pos;
        switch (varName) {
            case "s": pos = focus; break;
            default:
                throw new RuntimeException(String.format("%s semantic action argument %s was not literal or 's', 'p', or 'o'", SemActIri, varName));
        }
        return pos.toString();
    }

    private static String resolveTripleVar(String varName, Triple triple) {
        if (varName.charAt(0) == '"')
            return varName.replaceAll("\\\\(.)", "$1");

        Node pos;
        switch (varName) {
            case "s": pos = triple.getSubject(); break;
            case "p": pos = triple.getPredicate(); break;
            case "o": pos = triple.getObject(); break;
            default:
                throw new RuntimeException(String.format("%s semantic action argument %s was not a literal or 's', 'p', or 'o'", SemActIri, varName));
        }
        return pos.toString();
    }
}
