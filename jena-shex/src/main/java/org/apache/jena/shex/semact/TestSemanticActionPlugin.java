package org.apache.jena.shex.semact;

import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.SemAct;
import org.apache.jena.shex.expressions.TripleExpression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TestSemanticActionPlugin implements SemanticActionPlugin {

    @Override
    public List<String> getUris() {
        List<String> uris = new ArrayList<>();
        uris.add("http://shex.io/extensions/Test/");
        return uris;
    }

    @Override
    public boolean evaluate(SemAct semAct, TripleExpression tripleExpression, Collection<Triple> triples) {
        return semAct.getCode().indexOf("fail") == -1;
    }
}
