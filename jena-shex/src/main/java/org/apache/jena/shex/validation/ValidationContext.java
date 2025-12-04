/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jena.shex.validation;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexStatus;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.reporting.*;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.sys.SysShex;

import java.util.*;

public class ValidationContext {

    private final ValidationStack stack;
    private final ShexSchema schema;
    private final ShexSchemaMem schemaMem;
    private final Map<String, SemanticActionPlugin> semActPluginIndex;
    private final Graph graph;
    private final Typing typing;

    public ValidationContext(ShexSchema schema, Graph graph, boolean fixedSchema, boolean fixedGraph, Map<String, SemanticActionPlugin> semActPluginIndex) {
        this.schema = schema;
        this.graph = graph;
        this.semActPluginIndex = semActPluginIndex;

        this.schemaMem = new ShexSchemaMem(schema);
        if (fixedGraph) this.typing = new Typing();
        else this.typing = new EmptyTyping();

        this.stack = new ValidationStack();
    }

    public Report validate(Node focus, Node label, Reporter factory) {
        Report r = typing.get(focus, label);
        if (r != null)
            return r;
        Reporter myReport = factory.createRoot(focus, ShapeExprRef.create(label));
        computeIsValid(focus, label, myReport);
        return myReport.getReport();
    }

    private boolean computeIsValid(Node focus, Node label, Reporter reporter) {
        // Notifies the reporter about conformant / non-conformant.

        List<Node> nonAbstractDescendants =
                label == SysShex.startNode
                        ? List.of(label)
                        : nonAbstractDescendants(label);
        for (Node descendant : nonAbstractDescendants) {
            ShapeExpr expr = schema.get(descendant).getShapeExpr();
            Reporter exprReporter = reporter.createChild(focus, expr, null);
            exprReporter.informValidatingDescendant(descendant);

            stack.push(focus, descendant);
            boolean isValid = ShapeExprEval.satisfies(focus, expr, this, exprReporter);
            stack.pop();

            if (isValid) {
                reporter.informDescendantConformance(true, descendant);
                return reporter.setIsConformant(true);
            }
        }
        if (isExetendable(label))
            reporter.informDescendantConformance(false, null);
        return reporter.setIsConformant(false);
        // TODO memoization
    }

    /* package */ boolean validate(Node focus, ShapeExprRef shapeExprRef, Reporter reporter) {
        // Notifies the reporter about conformant / non-conformant.

        // The node has already been validated against this label and the result is known
        Node shapeExprLabel = shapeExprRef.getLabel();
        Report re = typing.get(focus, shapeExprLabel);
        if (re != null) {
            reporter.setReferenceTo(re, "");
            return re.getStatus() == ShexStatus.conformant;
        }
        //return reporter.setIsConformant(re.getStatus() == ShexStatus.conformant, "", re);

        // The node/label pair is on the stack
        if (stack.contains(focus, shapeExprLabel)) {
            reporter.addInfoCycle();
            return reporter.setIsConformant(true);
        }

        // The node has not been validated against this label
        return computeIsValid(focus, shapeExprLabel, reporter);
    }

    /** Duplicates-free list of the non-abstract subtypes, including the given shape declaration. */
    public List<Node> nonAbstractDescendants(Node shexprLabel) {
        return schemaMem.getTypeHierarchyGraph().getNonAbstractSubtypes(shexprLabel);
    }

    private boolean isExetendable(Node label) {
        return schemaMem.getTypeHierarchyGraph().isExtendableLabel(label);
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
                reporter.informSemanticActionsConformance(eval, semAct);
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
                reporter.informSemanticActionsConformance(eval, semAct);
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

    public ShapeDecl getShapeDecl (Node shapeExprLabel) {
        return this.schema.get(shapeExprLabel);
    }

    public TripleExprForValidation getExprForValidation(TripleExpr value) {
        return this.schemaMem.getSorbeFactory().getValExpr(value);
    }


    private static class ValidationStack {

        private Deque<Pair<Node, Node>> stack = new ArrayDeque<>();

        void push(Node focus, Node shapeExprLabel) {
            Pair<Node, Node> p = new Pair<>(focus, shapeExprLabel);
            stack.push(p);
        }

        // TODO check this Pair's hash code and equals are as we want them
        Pair<Node, Node> pop() {
            return stack.pop();
        }

        boolean contains(Node focus, Node shapeExprLabel) {
            return stack.contains(new Pair<>(focus, shapeExprLabel));
        }
    }

    static class TripleExprForValidationFactory {

        private final EMap<TripleExpr, TripleExprForValidation> sourceToTEValMap = new EMap<>();
        private final ShexSchema schema;

        private TripleExprForValidationFactory(ShexSchema schema) {
            this.schema = schema;
        }

        TripleExprForValidation getValExpr(TripleExpr tripleExpr) {
            return sourceToTEValMap.computeIfAbsent(tripleExpr, e -> TripleExprForValidation.create(tripleExpr, schema));
        }
    }

    /** Used to memorize static analysis information about a ShEx schema. */
    private static class ShexSchemaMem {

        private final TripleExprForValidationFactory sorbeFactory;
        private final TypeHierarchyGraph typeHierarchyGraph;

        public ShexSchemaMem(ShexSchema schema) {
            this.sorbeFactory = new TripleExprForValidationFactory(schema);
            this.typeHierarchyGraph = TypeHierarchyGraph.create(schema.getShapeMap());
        }

        public TripleExprForValidationFactory getSorbeFactory() {
            return this.sorbeFactory;
        }

        public TypeHierarchyGraph getTypeHierarchyGraph() {
            return this.typeHierarchyGraph;
        }

    }

    private static class Typing {

        private final Map<Pair<Node, Node>, Report> typing = new HashMap<>();

        /** Returns null if the result is unknown. */
        Report get(Node focus, Node shapeExprLabel) {
            return typing.get(new Pair<>(focus, shapeExprLabel));
        }

        void put(Node focus, Node shapeExprLabel, Report report) {
            typing.put(new Pair<>(focus, shapeExprLabel), report);
        }
    }

    private static class EmptyTyping extends Typing {

        final ExpressionHReport get(Node focus, Node shapeExprLabel) {
            return null;
        }

        final void put(Node focus, Node shapeExprLabel, Report report) { /*empty*/ }
    }


}




