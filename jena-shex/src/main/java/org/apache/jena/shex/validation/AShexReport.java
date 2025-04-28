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
    private ShexStatus status = null;
    private final List<ReportInfo> infos = new ArrayList<>();
    // One of expr and shapeDecl is not null
    public final ShapeExpr expr;
    public final ShapeDecl shapeDecl;
    public final AShexReport parent;
    private final List<AShexReport> children = new ArrayList<>();

    protected AShexReport(Node node, ShapeExpr expr, ShapeDecl shapeDecl, AShexReport parent) {
        if (expr == null && shapeDecl == null)
            throw new IllegalArgumentException("Either expr or shapeDecl is required.");
        if (expr != null &&
                ! (expr instanceof NodeConstraint || expr instanceof Shape || expr instanceof ShapeExprRef))
            throw new IllegalArgumentException("Expression must be an atomic shaphe expression (node constraint, shape, or reference).");
        this.node = node;
        this.expr = expr;
        this.shapeDecl = shapeDecl;
        this.parent = parent;
    }

    // TODO these create a ReportInfo, for success with some dummy message
    // subNeighbourhood is null if non relevant, TODO javadoc
    public abstract void addInfoSuccess(Expression expr, Node node, Set<Triple> subNeighbourhood);
    public abstract void addInfoFailure(Expression expr, Node node, Set<Triple> subNeighbourhood, String errorMessage);

    public abstract AShexReport createChild(Node node, ShapeExpr expr, ShapeDecl shapeDecl);

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
     * The {@link ShapeDecl} or atomic {@link ShapeExpr} to which the report is associated.
     */
    public Object getSchemaElement() {
        return expr != null ? expr : shapeDecl;
    }
}
