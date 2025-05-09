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
public class ExhaustiveShexReportElement extends AbstractShexReportElement {

    // TODO make a real factory
    public static ExhaustiveShexReportElement factory() {
        return new ExhaustiveShexReportElement();
    }

    private ExhaustiveShexReportElement(){}

    public ExhaustiveShexReportElement create (Node node, ShapeExpr expr) {
        return new ExhaustiveShexReportElement(node, expr, null);
    }

    public static ExhaustiveShexReportElement create(Node node, ShapeDecl shapeDecl) {
        return new ExhaustiveShexReportElement(node, ShapeExprRef.create(shapeDecl.getLabel()), null);
    }

    protected ExhaustiveShexReportElement(Node node, ShapeExpr expr, ShexReportElement parent) {
        super(node, expr, parent);
    }

    @Override
    public void addInfoSuccess(String message, Expression subExpr, Set<Triple> subNeighbourhood) {
        infos.add(new ReportInfoImpl(ShexStatus.conformant, "", subExpr, subNeighbourhood));
    }

    @Override
    public void addInfoFailure(String errorMessage, Expression expr, Set<Triple> subNeighbourhood) {
        infos.add(new ReportInfoImpl(ShexStatus.nonconformant, errorMessage, expr, subNeighbourhood));
    }

    @Override
    public AbstractShexReportElement createChild(Node node, ShapeExpr expr) {
        return new ExhaustiveShexReportElement(node, expr, this);
    }
}
