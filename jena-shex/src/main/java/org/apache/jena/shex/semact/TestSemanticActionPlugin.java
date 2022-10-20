package org.apache.jena.shex.semact;

import org.apache.jena.base.Sys;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.SemAct;
import org.apache.jena.shex.expressions.ShapeExpression;
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

    List<String> out = new ArrayList<>();

    public List<String> getOut () { return out; }

    @Override
    public boolean evaluateShapeExpr(SemAct semAct, ShapeExpression shapeExpression, Node focus) {
        return semAct.getCode().indexOf("fail") == -1;
    }

    @Override
    public boolean evaluateTripleExpr(SemAct semAct, TripleExpression tripleExpression, Collection<Triple> triples) {

        return semAct.getCode().indexOf("fail") == -1;
    }
}
