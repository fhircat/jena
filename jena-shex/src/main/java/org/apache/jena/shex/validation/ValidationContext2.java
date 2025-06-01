package org.apache.jena.shex.validation;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.SemAct;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.expressions.TripleExpr;
import org.apache.jena.shex.reporting.*;
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

        this.schemaMem = new ShexSchemaMem(schema);
        if (fixedGraph) this.typing = new Typing();
        else this.typing = new EmptyTyping();

        this.stack = new ValidationStack();
    }

    public ReportElement validate(Node focus, Node label, Reporter factory) {
        ShapeExprRef ref = ShapeExprRef.create(label);
        Reporter myReporter = factory.createRoot(focus, ref);
        validate(focus, ref, myReporter);
        return myReporter.getReport();
    }

    /* package */ boolean validate(Node focus, ShapeExprRef shapeExprRef, Reporter reporter) {
        // The node has already been validated against this label and the result is known
        Node shapeExprLabel = shapeExprRef.getLabel();
        ReportElement re = typing.get(focus, shapeExprLabel);
        if (re != null)
            return re.getStatus() == ShexStatus.conformant;

        // The node/label pair is on the stack
        if (stack.contains(focus, shapeExprLabel)) {
            reporter.setResult(ShexStatus.conformant, "Cycle.");
            return true;
        }

        // The node has not been validated against this label
        boolean isValid;
        stack.push(focus, shapeExprLabel);
        try {
            ShapeExpr expr = schema.get(shapeExprLabel).getShapeExpr();
            Reporter childReporter = reporter.createChild(focus, expr, null);
            isValid = ShapeExprEval.satisfies(focus, expr, this, childReporter);
        } finally { // TODO What exception could we have here ?
            stack.pop();
        }
        return reporter.setIsConformant(isValid);
    }

    /** Duplicates-free list of the non-abstract subtypes, including the given shape declaration. */
    public List<Node> getNonAbstractSubtypes(Node shexprLabel) {
        return schemaMem.getTypeHierarchyGraph().getNonAbstractSubtypes(shexprLabel);
    }

    public Graph getGraph() {
        return this.graph;
    }

    public boolean dispatchStartSemanticAction(ShexSchema schema) {
        for (SemAct semAct: schema.getSemActs()) {
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semAct.getIri());
            if (semActPlugin != null) {
                boolean eval = semActPlugin.evaluateStart(semAct, schema);
                if (!eval) return false;
            }
        }
        return true;
    }

    public boolean dispatchShapeExprSemanticAction(Node focus, ShapeExpr expr, Reporter reporter) {
        if (expr.getSemActs() == null)
            return true;
        for (SemAct semAct: expr.getSemActs()) {
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semAct.getIri());
            if (semActPlugin != null) {
                boolean eval = semActPlugin.evaluateShapeExpr(semAct, expr, focus);
                reporter.addSemanticActionsInfo(
                        eval ? ShexStatus.conformant : ShexStatus.nonconformant,
                        eval ? "Semantic actions satisfied" : "Semantic actions not satisfied",
                        semAct);
                if (!eval) return false;
            }
        }
        return true;
    }

    public boolean dispatchTripleExprSemanticAction(TripleExpr expr, Set<Triple> triples, Reporter reporter, Node node) {
        if (expr.getSemActs() == null)
            return true;
        for (SemAct semAct : expr.getSemActs()) {
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semAct.getIri());
            if (semActPlugin != null) {
                boolean eval = semActPlugin.evaluateTripleExpr(semAct, expr, triples);
                reporter.addSemanticActionsInfo(
                        eval ? ShexStatus.conformant : ShexStatus.nonconformant,
                        eval ? "Semantic actions satisfied" : "Semantic actions not satisfied",
                        semAct);
                if (!eval) return false;
            }
        }
        return true;
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


    private static class ValidationStack {

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

    private static class ShexSchemaMem {

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

    private static class Typing {

        private final Map<Pair<Node, Node>, ReportElement> typing = new HashMap<>();

        /** Returns null if the result is unknown. */
        ReportElement get(Node focus, Node shapeExprLabel) {
            return typing.get(new Pair<>(focus, shapeExprLabel));
        }

        void put(Node focus, Node shapeExprLabel, ReportElement report) {
            typing.put(new Pair<>(focus, shapeExprLabel), report);
        }
    }

    private static class EmptyTyping extends Typing {

        final ExpressionHReport get(Node focus, Node shapeExprLabel) {
            return null;
        }

        final void put(Node focus, Node shapeExprLabel, ReportElement report) { /*empty*/ }
    }


}




