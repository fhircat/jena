package org.apache.jena.shex.semact;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.Plugin;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.SemAct;
import org.apache.jena.shex.expressions.ShapeExpression;
import org.apache.jena.shex.expressions.TripleExpression;
import org.apache.jena.shex.sys.SysShex;

import java.util.Collection;
import java.util.List;

public interface SemanticActionPlugin extends Plugin {

    default void register() {
        List<String> uris = getUris();
        if(uris != null && !uris.isEmpty()) {
            uris.forEach(uri -> SysShex.registerSemActPlugin(uri, this));
        }
    }

    List<String> getUris();

    boolean evaluateStart(SemAct semAct, ShexSchema schema);

    boolean evaluateTripleExpr(SemAct semAct, TripleExpression tripleExpression, Collection<Triple> triples);

    boolean evaluateShapeExpr(SemAct semAct, ShapeExpression shapeExpression, Node focus);
}
