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

package org.apache.jena.shex.sys;

import java.util.*;

import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.*;
import org.apache.jena.shex.eval.SorbeHandler;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.TripleExpr;
import org.apache.jena.shex.semact.SemanticActionPlugin;

/**
 * Context for a validation and collector of the results.
 */
public class ValidationContext {
    // TODO Generation of reports is not tested
    private final ShexSchema schema;
    private final Graph data;
    private final SorbeHandler sorbeHandler;
    private ValidationContext parentCtx = null;
    private Map<String, SemanticActionPlugin> semActPluginIndex;
    // <data node, shape decl>
    // TODO possible efficiency problem. ShapeDecl's equals goes recursively through the structure of the expression, so each time we test whether there is a loop, we execute equals again and again. Possible solution: stack only the ref, not the whole shape decl
    private Deque<Pair<Node, ShapeDecl>> inProgress = new ArrayDeque<>();

    private final ShexReport.Builder reportBuilder = ShexReport.create();

    /** @deprecated Use method {@link #create()} */
    @Deprecated
    public static ValidationContext create(ValidationContext vCxt) {
        return vCxt.create();
    }

    public ValidationContext(Graph data, ShexSchema schema, Map<String, SemanticActionPlugin> semActPluginIndex) {
        this(null, data, schema, null, semActPluginIndex, new SorbeHandler());
    }

    private ValidationContext(ValidationContext parentCtx, Graph data, ShexSchema schema,
                              Deque<Pair<Node, ShapeDecl>> progress,
                              Map<String, SemanticActionPlugin> semActPluginIndex,
                              SorbeHandler sorbeHandler) {
        this.parentCtx = parentCtx;
        this.data = data;
        this.schema = schema;
        this.semActPluginIndex = semActPluginIndex;
        if (progress != null)
            this.inProgress.addAll(progress);
        this.sorbeHandler = sorbeHandler;
    }

    public ValidationContext getParent() {
        return parentCtx;
    }

    public SorbeHandler getSorbeHandler() {
        return sorbeHandler;
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

    public Graph getData() {
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
        return new ValidationContext(this, this.data, this.schema, this.inProgress, this.semActPluginIndex, this.sorbeHandler);
    }

    public void startValidate(ShapeDecl shape, Node data) {
        inProgress.push(Pair.create(data, shape));
    }

    // Return true if done or in-progress (i.e. don't walk further)
    public boolean cycle(ShapeDecl shapeDecl, Node data) {
        return inProgress.stream().anyMatch(p -> p.equalElts(data, shapeDecl));
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
        return se.getSemActs().stream().noneMatch(semAct -> {
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semAct.getIri());
            if (semActPlugin != null) {
                return !semActPlugin.evaluateShapeExpr(semAct, se, focus);
            }
            return false;
        });
    }

    public boolean dispatchTripleExprSemanticAction(TripleExpr te, Set<Triple> matchables) {
        return te.getSemActs().stream().noneMatch(semAct -> {
            SemanticActionPlugin semActPlugin = this.semActPluginIndex.get(semAct.getIri());
            if (semActPlugin != null) {
                return !semActPlugin.evaluateTripleExpr(semAct, te, matchables);
            }
            return false;
        });
    }

    public void finishValidate(ShapeDecl shape, Node data) {
        Pair<Node, ShapeDecl> x = inProgress.pop();
        if (x.equalElts(data, shape))
            return;
        throw new InternalErrorException("Eval stack error");
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
}
