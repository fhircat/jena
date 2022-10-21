package org.apache.jena.shex.semact;

import org.apache.jena.base.Sys;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.SemAct;
import org.apache.jena.shex.expressions.ShapeExpression;
import org.apache.jena.shex.expressions.TripleExpression;

import javax.naming.OperationNotSupportedException;
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
        throw new RuntimeException("This feature is currently not supported");
    }

    @Override
    public boolean evaluateTripleExpr(SemAct semAct, TripleExpression tripleExpression, Collection<Triple> triples) {
        if(semAct.getCode().indexOf("fail") >= 0) {
            return false;
        } else {
            out.add(semAct.getCode());
            return true;
        }
    }
}
