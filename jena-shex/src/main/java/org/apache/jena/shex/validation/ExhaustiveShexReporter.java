package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.ShapeExprRef;

import java.util.Set;

/**
 * The result of validating a node against a {@link ShapeDecl} or {@link Shape}.
 */
public class ExhaustiveShexReporter extends AShexReport {

    public static ExhaustiveShexReporter create(Node node, ShapeDecl shapeDecl) {
        return new ExhaustiveShexReporter(node, ShapeExprRef.create(shapeDecl.getLabel()), null);
    }

    protected ExhaustiveShexReporter(Node node, ShapeExpr expr, AShexReport parent) {
        super(node, expr, parent);
    }

    @Override
    public void addInfoSuccess(Expression expr, Set<Triple> subNeighbourhood) {
        infos.add(new ReportInfoImpl(expr, subNeighbourhood, ShexStatus.conformant, ""));
    }

    @Override
    public void addInfoFailure(Expression expr, Set<Triple> subNeighbourhood, String errorMessage) {
        infos.add(new ReportInfoImpl(expr, subNeighbourhood, ShexStatus.nonconformant, errorMessage));
    }

    @Override
    public AShexReport createChild(Node node, ShapeExpr expr) {
        return new ExhaustiveShexReporter(node, expr, this);
    }
}
