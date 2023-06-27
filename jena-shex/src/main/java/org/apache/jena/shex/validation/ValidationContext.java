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

import java.util.*;

import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.*;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.TripleExpr;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.sys.ReportItem;

/**
 * Context for a validation and collector of the results.
 */
public class ValidationContext {
    // TODO Generation of reports is not tested
    private final ShexSchema schema;
    private final Graph data;
    private ValidationContext parentCtx = null;
    private final Map<String, SemanticActionPlugin> semActPluginIndex;
    private final SorbeFactory sorbeFactory;
    private final TypeHierarchyGraph typeHierarchyGraph;
    private final Deque<ValidationStackElement> validationStack;

    private final ShexReport.Builder reportBuilder = ShexReport.create();

    /** @deprecated Use method {@link #create()} */
    @Deprecated
    public static ValidationContext create(ValidationContext vCxt) {
        return vCxt.create();
    }

    public ValidationContext(Graph data, ShexSchema schema, Map<String, SemanticActionPlugin> semActPluginIndex) {
            this(null, data, schema, new ArrayDeque<>(), semActPluginIndex, new SorbeFactory(schema));
    }

    private ValidationContext(ValidationContext parentCtx, Graph data, ShexSchema schema,
                              Deque<ValidationStackElement> progress,
                              Map<String, SemanticActionPlugin> semActPluginIndex,
                              SorbeFactory sorbeFactory) {
        this.parentCtx = parentCtx;
        this.data = data;
        this.schema = schema;
        this.semActPluginIndex = semActPluginIndex;
        this.validationStack = new ArrayDeque<>();
        this.validationStack.addAll(progress); // TODO copying the stack ?
        this.sorbeFactory = sorbeFactory;
        //this.typeHierarchyGraph = TypeHierarchyGraph.create(sorbeFactory);
        this.typeHierarchyGraph = null;
    }

    public ValidationContext getParent() {
        return parentCtx;
    }

    public ValidationContext getRoot() {
        ValidationContext parent = this.parentCtx;
        while (parent != null) {
            parent = this.getParent();
        }
        return (parent != null) ? parent : this;
    }

    public TripleExpr getTripleExpr(Node label) {
        return schema.getTripleExpr(label);
    }

    public ShexSchema getSchema() {
        return schema;
    }

    public ShapeDecl getShapeDecl(Node label) {
        return schema.get(label);
    }

    public Graph getGraph() {
        return data;
    }

    /**
     * Creates a new validation context with the current one as its parent context.
     * Initializes the new context with the state of the parent context.
     *
     * @return new ValidationContext with this as parent.
     */
    public ValidationContext create() {
        // Fresh ShexReport.Builder
        return new ValidationContext(this, this.data, this.schema,
                this.validationStack, this.semActPluginIndex, this.sorbeFactory);
    }

    public void startValidate(ShapeDecl shape, Node data) {
        validationStack.push(new ValidationStackElement(data, shape));
    }

    public void finishValidate(ShapeDecl shape, Node data) {
        // TODO this check seems to be a debugging functionality, remove it ?
        if (! validationStack.pop().equals(new ValidationStackElement(data, shape)))
            throw new InternalErrorException("Eval stack error");
    }

    public boolean cycle(Node dataNode, ShapeDecl shapeDecl) {
        ValidationStackElement el = new ValidationStackElement(dataNode, shapeDecl);
        return validationStack.stream().anyMatch(p -> p.equals(el));
    }

    public boolean dispatchStartSemanticAction(ShexSchema schema, ValidationContext vCxt) {
        return schema.getSemActs().stream().noneMatch(semAct -> {
            String semActIri = semAct.getIri();
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semActIri);
            if (semActPlugin != null) {
                if (!semActPlugin.evaluateStart(semAct, schema)) {
                    vCxt.reportEntry(new ReportItem(String.format("%s start shape failed", semActIri), null));
                    return true;
                }
            }
            return false;
        });
    }

    public boolean dispatchShapeExprSemanticAction(ShapeExpr se, Node focus) {
        if (se.getSemActs() == null)
            return true;
        return se.getSemActs().stream().noneMatch(semAct -> {
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semAct.getIri());
            if (semActPlugin != null) {
                return !semActPlugin.evaluateShapeExpr(semAct, se, focus);
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


    /**
     * Update other with "this" state
     */
    public void copyInto(ValidationContext other) {
        reportBuilder.getItems().forEach(item -> other.reportEntry(item));
        reportBuilder.getReports().forEach(reportLine -> other.shexReport(reportLine));
    }

    private void shexReport(ShexRecord reportLine) {
        reportBuilder.shexReport(reportLine);
    }

    /**
     * Current state.
     */
    public List<ReportItem> getReportItems() {
        return reportBuilder.getItems();
    }

    /**
     * Current state.
     */
    public List<ShexRecord> getShexReportItems() {
        return reportBuilder.getReports();
    }

    public void reportEntry(ReportItem item) {
        reportBuilder.addReportItem(item);
    }

    public void shexReport(ShexRecord entry, Node focusNode, ShexStatus result, String reason) {
        reportBuilder.shexReport(entry, focusNode, result, reason);
    }

    public ShexReport generateReport() {
        return reportBuilder.build();
    }

    public SorbeTripleExpr getSorbe(TripleExpr tripleExpr) {
        return sorbeFactory.getSorbe(tripleExpr);
    }

    private static class ValidationStackElement extends Pair<Node, ShapeDecl> {

        public ValidationStackElement(Node dataNode, ShapeDecl shapeDecl) {
            super(dataNode, shapeDecl);
        }

        public ShapeDecl getShapeDecl () {
            return getRight();
        }

        public Node getDataNode () {
            return getLeft();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getDataNode(), getShapeDecl().getLabel());
        }

        @Override
        public boolean equals(Object other) {
            if ( getClass() != other.getClass() )
                return false;
            ValidationStackElement e = (ValidationStackElement) other;
            return Objects.equals(getDataNode(), e.getDataNode())
                    && Objects.equals(getShapeDecl().getLabel(), e.getShapeDecl().getLabel());
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
}
