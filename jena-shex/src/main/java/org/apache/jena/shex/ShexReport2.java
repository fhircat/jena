package org.apache.jena.shex;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.sys.ReportItem;

import java.util.ArrayList;
import java.util.List;

public class ShexReport2 {

    private ShexStatus status = null;
    public final Node node;
    private final List<ReportItem> reports = new ArrayList<>();
    // One of expr and shapeDecl is not null
    public Expression expr;
    public ShapeDecl shapeDecl;

    public final ShexReport2 parent;
    private final List<ShexReport2> children = new ArrayList<>();

    public static ShexReport2 get(ShapeDecl shapeDecl, Node node) {
        return new ShexReport2(null, shapeDecl, node, null);
    }

    private ShexReport2(Expression expr, ShapeDecl shapeDecl,  Node node, ShexReport2 parent) {
        this.expr = expr;
        this.shapeDecl = shapeDecl;
        this.node = node;
        this.parent = parent;
    }

    public ShexStatus getStatus() {
        return status;
    }

    public ShexReport2 createChild(ShapeExpr se, Node dataNode) {
        ShexReport2 child = new ShexReport2(se, null, dataNode, this);
        children.add(child);
        return child;
    }

    public ShexReport2 createChild(ShapeDecl shapeDecl, Node dataNode) {
        ShexReport2 child = new ShexReport2(null, shapeDecl, dataNode, this);
        children.add(child);
        return child;
    }

    public void setSatisfies(boolean satisfies) {
        if (satisfies)
            status = ShexStatus.conformant;
        else
            status = ShexStatus.nonconformant;
    }

    // TODO go to all usages and see whether report items are needed
    public void setSatisfied2() {
        status = ShexStatus.conformant;
    }

    // TODO go through all usages and see whether ReportItems are needed
    public void setUnsatisfied2() {
        status = ShexStatus.nonconformant;
    }

    // TODO make it the only one that creates a ReportItem. This allows to disable detailed reporting
    // TODO maybe different versions are needed, each with appropriate set of parameters
    public void addReport(String msg) {
        reports.add(new ReportItem(msg));
    }

    // TODO to be used only with node constraints for now
    public void addNodeConstraintReport(String msg, Node dataNode) {
        reports.add(new ReportItem(msg, dataNode));
    }


}
