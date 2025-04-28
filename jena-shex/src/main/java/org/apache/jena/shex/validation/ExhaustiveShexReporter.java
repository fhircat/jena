package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExpr;
import java.util.Set;

/**
 * The result of validating a node against a {@link ShapeDecl} or {@link Shape}.
 */
public class ExhaustiveShexReporter extends AShexReport {

    public static ExhaustiveShexReporter create(ShapeDecl shapeDecl, Node node) {
        return new ExhaustiveShexReporter(node, null, shapeDecl, null);
    }

    protected ExhaustiveShexReporter(Node node, ShapeExpr expr, ShapeDecl shapeDecl, AShexReport parent) {
        super(node, expr, shapeDecl, parent);
    }

    @Override
    public void addInfoSuccess(Expression expr, Node node, Set<Triple> subNeighbourhood) {

    }

    @Override
    public void addInfoFailure(Expression expr, Node node, Set<Triple> subNeighbourhood, String errorMessage) {

    }

    @Override
    public AShexReport createChild(Node node, ShapeExpr expr, ShapeDecl shapeDecl) {
        return new ExhaustiveShexReporter(node, expr, shapeDecl, this);
    }
}
