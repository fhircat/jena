package org.apache.jena.shex.validation;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.SemAct;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.expressions.TripleExpr;
import org.apache.jena.shex.semact.SemanticActionPlugin;

import java.util.*;

public class ValidationContext2 {

    private final ValidationStack stack;
    private final ShexSchema schema;
    private final ShexSchemaMem schemaMem;
    private final Map<String, SemanticActionPlugin> semActPluginIndex;
    private final Graph graph;
    private final Typing typing;

    public ValidationContext2 (ShexSchema schema, Graph graph, boolean fixedSchema, boolean fixedGraph, Map<String, SemanticActionPlugin> semActPluginIndex) {
        this.schema = schema;
        this.graph = graph;
        this.semActPluginIndex = semActPluginIndex;

        if (fixedSchema) this.schemaMem = new ShexSchemaMem(schema);
        else this.schemaMem = new ShexSchemaMem(schema);
        if (fixedGraph) this.typing = new Typing();
        else this.typing = new EmptyTyping();

        this.stack = new ValidationStack();
    }

    // Used from public
    public ShexReportElement validate(Node focus, ShapeExprRef shapeExprRef, ShexReportElement factory) {
        // The node has already been validated against this label and the result is known
        Node shapeExprLabel = shapeExprRef.getLabel();
        ShexReportElement r = typing.get(focus, shapeExprLabel);
        if (r != null)
            return new ShexReportReference(r);

        // The node/label pair is on the stack
        if (stack.contains(focus, shapeExprLabel)) {
            r = factory.create(focus, shapeExprRef);
            r.setSatisfies(true);
            r.addInfoSuccess("Cycle detected", null, null);
            return r;
        }

        // The node has not been validated against this label
        // TODO the stack
        // TODO the reference should be given, instead of created
        // TODO dispatch semantic actions

        boolean isValid = false;
        r = factory.create(focus, shapeExprRef);
        stack.push(focus, shapeExprLabel);
        try {
            ShapeExpr expr = schema.get(shapeExprLabel).getShapeExpr();
            isValid = ShapeExprEval.satisfies(focus, expr, this, r)
                && dispatchShapeExprSemanticAction(focus, expr);
        } finally { // TODO What exception could we have here ?
            stack.pop();
        }
        r.setSatisfies(isValid);
        return r;
    }

    public ShexReport validate(Collection<Pair<Node, Node>> shapeMap) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /** Duplicates-free list of the non-abstract subtypes, including the given shape declaration. */
    public List<Node> getNonAbstractSubtypes(Node shexprLabel) {
        return schemaMem.getTypeHierarchyGraph().getNonAbstractSubtypes(shexprLabel);
    }

    public Graph getGraph() {
        return this.graph;
    }

    public ShexReport dispatchStartSemanticAction(ShexSchema schema, ShexReportElement factory) {
        ShexReport.Builder builder = new ShexReport.Builder();
        List<SemAct> semACts = schema.getSemActs();
        for (SemAct semAct: semACts) {
            String semActIri = semAct.getIri();
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semActIri);
            if (semActPlugin != null) {
                if (!semActPlugin.evaluateStart(semAct, schema)) {
                    ShexReportElement r = ExhaustiveShexReportElement.factory(); // the passed ShexReportElement factory doesn't accept create(null, null)
                    r.setSatisfies(false);
                    r.addInfoFailure(String.format("%s start shape failed", semActIri), null, null);
                    builder.addReport(null, null, r);
                }
            }
        }
        return builder.build();
    }

    public boolean dispatchShapeExprSemanticAction(Node focus, ShapeExpr expr) {
        if (expr.getSemActs() == null)
            return true;
        return expr.getSemActs().stream().noneMatch(semAct -> {
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semAct.getIri());
            if (semActPlugin != null) {
                return !semActPlugin.evaluateShapeExpr(semAct, expr, focus);
            }
            return false;
        });
    }

    public boolean dispatchTripleExprSemanticAction(TripleExpr te, Set<Triple> matchables) {
        if (te.getSemActs() == null)
            return true;
        return te.getSemActs().stream().noneMatch(semAct -> {
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semAct.getIri());
            if (semActPlugin != null) {
                return !semActPlugin.evaluateTripleExpr(semAct, te, matchables);
            }
            return false;
        });
    }

    public TripleExpr getTripleExpr(Node label) {
        return schema.getTripleExpr(label);
    }

    /* Duplicates-free list of the supertypes (ie extended shape declarations), including the given shape declaration. */
    public List<Node> getSupertypes(Node shexprLabel) {
        return schemaMem.getTypeHierarchyGraph().getSupertypes(shexprLabel);
    }

    public ShapeExpr getDefinition (Node shapeExprLabel) {
        return this.schema.get(shapeExprLabel).getShapeExpr();
    }

    public SorbeTripleExpr getSorbe(TripleExpr value) {
        return this.schemaMem.getSorbeFactory().getSorbe(value);
    }


    private class ValidationStack {

        private Deque<Pair<Node, Node>> stack = new ArrayDeque<>();

        void push(Node focus, Node shapeExprLabel) {
            Pair<Node, Node> p = new Pair<>(focus, shapeExprLabel);
            stack.push(p);
        }

        // TODO chec this Pair's hash code and equals are as we want them
        Pair<Node, Node> pop() {
            return stack.pop();
        }

        boolean contains(Node focus, Node shapeExprLabel) {
            return stack.contains(new Pair<>(focus, shapeExprLabel));
        }
    }

    static class SorbeFactory {

        private final EMap<TripleExpr, SorbeTripleExpr> sourceToSorbeMap = new EMap<>();
        private final ShexSchema schema;

        private SorbeFactory(ShexSchema schema) {
            this.schema = schema;
        }

        SorbeTripleExpr getSorbe (TripleExpr tripleExpr) {
            return sourceToSorbeMap.computeIfAbsent(tripleExpr, e -> SorbeTripleExpr.create(tripleExpr, schema));
        }
    }

    private class ShexSchemaMem {

        private final SorbeFactory sorbeFactory;
        private final TypeHierarchyGraph typeHierarchyGraph;

        public ShexSchemaMem(ShexSchema schema) {
            this.sorbeFactory = new SorbeFactory(schema);
            this.typeHierarchyGraph = TypeHierarchyGraph.create(schema.getShapeMap());
        }

        public SorbeFactory getSorbeFactory() {
            return this.sorbeFactory;
        }

        public TypeHierarchyGraph getTypeHierarchyGraph() {
            return this.typeHierarchyGraph;
        }

    }

    private class Typing {

        private Map<Pair<Node, Node>, ShexReportElement> typing = new HashMap<>();

        /** Returns null if the result is unknown. */
        ShexReportElement get(Node focus, Node shapeExprLabel) {
            return typing.get(new Pair<>(focus, shapeExprLabel));
        }
    }

    private class EmptyTyping extends Typing {

        final ShexReportElement get(Node focus, Node shapeExprLabel) {
            return null;
        }
    }

    private static class ShexReportReference implements ShexReportElement {

        private ShexReportElement parent = null;
        private final ShexReportElement report;

        ShexReportReference(ShexReportElement report) {
            super();
            this.report = report;
        }

        @Override
        public void setParent(ShexReportElement parent) {
            if (this.parent != null) {
                throw new IllegalStateException("Can't set parent twice");
            }
            this.parent = parent;
        }

        @Override
        public void setSatisfies(boolean satisfies) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addInfoSuccess(String message, Expression subExpr, Set<Triple> subNeighbourhood) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addInfoFailure(String errorMessage, Expression expr, Set<Triple> subNeighbourhood) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShexReportElement create(Node node, ShapeExpr shapeExpr) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Node getNode() {
            return report.getNode();
        }

        @Override
        public ShapeExpr getShapeExpr() {
            return report.getShapeExpr();
        }

        @Override
        public ShexStatus getStatus() {
            return report.getStatus();
        }



        @Override
        public ShexReportElement getParent() {
            return parent;
        }

        @Override
        public List<ShexReportElement> getChildren() {
            return report.getChildren();
        }

        @Override
        public List<ReportInfo> getInfos() {
            return report.getInfos();
        }

    }


}




