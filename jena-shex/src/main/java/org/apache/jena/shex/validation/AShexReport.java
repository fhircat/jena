package org.apache.jena.shex.validation;


import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Result of validating a node against a {@link ShapeDecl} or a {@link Shape}.
 * Is a hierarchic structure.
 */
public abstract class AShexReport {

    /* TODO make ShexReport2 subclass of this one, and call it ExhaustiveShapeReporter or something like that
            ie all report infos are registered. Possibly, make also a minimal implementation that ignores all
            registered infos. */

    public final Node node;
    public final ShapeExpr expr;
    public final AShexReport parent;
    private final List<AShexReport> children = new ArrayList<>();

    private ShexStatus status = null;
    private final List<ReportInfo> infos = new ArrayList<>();

    protected AShexReport(Node node, ShapeExpr expr, AShexReport parent) {
        if (! (expr instanceof NodeConstraint || expr instanceof Shape || expr instanceof ShapeExprRef))
            throw new IllegalArgumentException("Expression must be an atomic shape expression (node constraint, shape, or reference).");
        this.node = node;
        this.expr = expr;
        this.parent = parent;
    }

    // TODO these create a ReportInfo, for success with some dummy message
    // subNeighbourhood is null if non relevant, TODO javadoc
    // TODO why do we need to give an expression and a node while the report already has those ?
    public abstract void addInfoSuccess(Expression expr, Node node, Set<Triple> subNeighbourhood);
    public abstract void addInfoFailure(Expression expr, Node node, Set<Triple> subNeighbourhood, String errorMessage);

    public abstract AShexReport createChild(Node node, ShapeExpr expr);

    public void setSatisfies (boolean satisfies) {
        this.status = satisfies ? ShexStatus.conformant : ShexStatus.nonconformant;
    }

    public ShexStatus getStatus() {
        return status;
    }

    public List<ReportInfo> getInfos() {
        return Collections.unmodifiableList(infos);
    }

    public List<AShexReport> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public AShexReport getParent() {
        return parent;
    }

    /**
     * The {@link ShapeExpr} to which the report is associated.
     */
    public ShapeExpr getSchapeExpr() {
        return expr;
    }
}
