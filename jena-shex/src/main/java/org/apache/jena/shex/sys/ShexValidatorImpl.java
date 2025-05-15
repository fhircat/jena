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

import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.atlas.lib.ListUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.*;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.reporting.AtomicExprHReport;
import org.apache.jena.shex.reporting.ExprHReportExhaustive;
import org.apache.jena.shex.reporting.ShexReport;
import org.apache.jena.shex.semact.SemanticActionPlugin;
import org.apache.jena.shex.validation.*;

import java.util.*;

class ShexValidatorImpl implements ShexValidator {

    private Map<String, SemanticActionPlugin> semanticActionPluginIndex;

    ShexValidatorImpl() {
    }

    ShexValidatorImpl(Map<String, SemanticActionPlugin> semActPluginIndex) {
        this.semanticActionPluginIndex = semActPluginIndex;
    }

    /**
     * Return the current system-wide {@code ShexValidator}.
     */
    public static ShexValidator get() {
        return SysShex.get();
    }

    /**
     * Validate data using a collection of shapes and a shape map
     */
    @Override
    public ShexReport validate(Graph dataGraph, ShexSchema shapes, ShapeMap shapeMap) {
        throw new UnsupportedOperationException("deprecated");
        /*Objects.requireNonNull(dataGraph);
        Objects.requireNonNull(shapes);
        Objects.requireNonNull(shapeMap);
        shapes = shapes.importsClosure();
        ValidationContext vCxt = new ValidationContext(dataGraph, shapes, semanticActionPluginIndex);
        List<Boolean> results = new ArrayList<>();
        shapeMap.entries().forEach(mapEntry -> {
            Collection<Node> focusNodes = focusNodes(dataGraph, mapEntry);
            if (focusNodes == null)
                throw new InternalErrorException("Shex shape mapping has no node and no pattern");
            // The validation work for this map entry.
            for (Node focus : focusNodes) {
                results.add(validationStep(vCxt, mapEntry, mapEntry.shapeExprLabel, focus));
            }
        });
        return new ShexReport(results.stream().allMatch(it -> it));*/
    }

    /**
     * Validate a specific node (the focus), with a specific shape in a set of shapes.
     */
    @Override
    public ShexReport validate(Graph dataGraph, ShexSchema shapes, Node shapeRef, Node focus) {
        throw new UnsupportedOperationException("deprecated");
        /*Objects.requireNonNull(shapeRef);
        Objects.requireNonNull(focus);
        Objects.requireNonNull(shapes);
        Objects.requireNonNull(dataGraph);
        ShapeMapElement entry = new ShapeMapElement(focus, shapeRef);
        ShexSchema schemaWithImports = shapes.importsClosure();
        ValidationContext vCxt = new ValidationContext(dataGraph, schemaWithImports, semanticActionPluginIndex);

        boolean isValid = vCxt.dispatchStartSemanticAction(schemaWithImports, vCxt);
        if (!isValid)
            report(vCxt, entry, focus, ShexStatus.nonconformant, null);
        else
            isValid = validationStep(vCxt, entry, shapeRef, focus);

        return new ShexReport(isValid);*/
    }

    /**
     * Validate a specific node (the focus), against a given shape.
     */
    @Override
    public ShexReport validate(Graph dataGraph, ShexSchema shapes, ShapeDecl shape, Node focus) {
        throw new UnsupportedOperationException("deprecated");
        /*Objects.requireNonNull(shape);
        Objects.requireNonNull(shapes);
        Objects.requireNonNull(dataGraph);
        Objects.requireNonNull(focus);
        ShapeMapElement entry = new ShapeMapElement(focus, shape.getLabel());
        shapes = shapes.importsClosure();
        ValidationContext vCxt = new ValidationContext(dataGraph, shapes, semanticActionPluginIndex);
        boolean started = vCxt.dispatchStartSemanticAction(shapes, vCxt);
        boolean isValid = validationStep(vCxt, entry, entry.shapeExprLabel, focus);
        return new ShexReport(isValid);*/
    }

    @Override
    public ShexReport validate(Graph dataGraph, ShexSchema shapes, ShapeMap shapeMap, Node focus) {
        throw new UnsupportedOperationException("deprecated");
        /*Objects.requireNonNull(shapes);
        Objects.requireNonNull(dataGraph);
        Objects.requireNonNull(shapeMap);
        Objects.requireNonNull(focus);
        shapes = shapes.importsClosure();
        ValidationContext vCxt = new ValidationContext(dataGraph, shapes, semanticActionPluginIndex);
        List<ShapeMapElement> reports = new ArrayList<>();
        boolean isValid = vCxt.dispatchStartSemanticAction(shapes, vCxt);
        shapeMap.entries().forEach(mapEntry -> {
            validateOneShapeRecord(vCxt, mapEntry, focus);
        });

        ShexReport report = vCxt.generateReport();
        return report;*/
    }

    // Execute validation if the focus node is in the scope of the shapeRecord.
    private static boolean validateOneShapeRecord(ValidationContext vCxt, ShapeMapElement shapeRecord, Node focusNode) {
        Collection<Node> focusNodes = focusNodes(vCxt.getGraph(), shapeRecord);
        if (focusNodes == null)
            throw new InternalErrorException("Shex shape mapping has no node and no pattern");
        if (!focusNodes.contains(focusNode))
            return true;
        return validationStep(vCxt, shapeRecord, shapeRecord.shapeExprLabel, focusNode);
    }

    private static Collection<Node> focusNodes(Graph graph, ShapeMapElement mapRecord) {
        if (mapRecord.nodeSelector != null) {
            return List.of(mapRecord.nodeSelector);
        }
        if (mapRecord.patternSelector != null) {
            Triple t = mapRecord.asMatcher();
            Collection<Node> focusNodes = graph.find(t)
                    .mapWith(triple -> focusFromRecord(mapRecord, triple))
                    .toSet();
            return focusNodes;
        }
        return null;
    }

    private static Node focusFromRecord(ShapeMapElement mapRecord, Triple triple) {
        if (mapRecord.isSubjectFocus())
            return triple.getSubject();
        if (mapRecord.isObjectFocus())
            return triple.getObject();
        return null;
    }

    // Entry point for all validation.
    private static boolean validationStep(ValidationContext vCxt, ShapeMapElement mapEntry, Node shapeRef, Node focus) {
        track(mapEntry.shapeExprLabel, focus);
        // Isolate.
        ShapeDecl shape = vCxt.getShapeDecl(shapeRef);
        if (shape == null) {
            // No such shape.
            vCxt.getShapeDecl(shapeRef); // TODO what's this for ? the value is not used, and no exception is raised
            String msg = "No such shape: " + ShexLib.displayStr(shapeRef);
            vCxt.reportEntry("No such shape: " + ShexLib.displayStr(shapeRef));
            report(vCxt, mapEntry, shapeRef, ShexStatus.nonconformant, msg);
            return false;
        }
        return validationStepWorker(vCxt, mapEntry, shape, shapeRef, focus);
    }

    // Worker.
    private static boolean validationStepWorker(ValidationContext vCxt, ShapeMapElement mapEntry, ShapeDecl shapeDecl,
                                                Node shapeRef, Node focus) {
        // TODO adapt to new reporting mechanism
        throw new UnsupportedOperationException("deprecated");

        // Isolate report entries.
        //ValidationContext vCxtInner = vCxt.create();
        //vCxtInner.startValidate(shapeDecl, focus);
        //AtomicExprHReport shexReport = ExprHReportExhaustive.create(focus, shapeDecl);
        //ShapeExprEval.satisfies(focus, shapeDecl.getShapeExpr(), vCxtInner, shexReport);
        //vCxtInner.finishValidate(shapeDecl, focus);
        //boolean isValid = shexReport.getStatus() == ShexStatus.conformant;
        //if (!isValid) {
        //    atLeastOneReportItem(vCxtInner, null, focus, shexReport);
        //    vCxtInner.copyInto(vCxt); // Report items.
        //}
        //createShexReportLine(vCxt, mapEntry, isValid, shapeRef, focus);
        //return isValid;
    }

    private static void createShexReportLine(ValidationContext vCxt, ShapeMapElement mapEntry, boolean conforms, Node shapeRef, Node focus) {
        // Shex shapes report.
        if (conforms) {
            report(vCxt, mapEntry, focus, ShexStatus.conformant, null);
            return;
        }

        if (mapEntry == null)
            return;

        ReportItem item = ListUtils.last(vCxt.getReportItems());
        String reason = (item != null) ? item.getMessage() : null;
        report(vCxt, mapEntry, focus, ShexStatus.nonconformant, reason);
    }

    // TODO review
    private static void atLeastOneReportItem(ValidationContext vCxt, ShapeExpr exprForReport /* TODO replace*/,
                                             Node focus, AtomicExprHReport shexReport) {
        // Ensure at least one entry.
        throw new UnsupportedOperationException("deprecated");
        //if (vCxt.getReportItems().isEmpty()) {
        //    shexReport.addInfoFailure(exprForReport, null, "Failed");
        //}
    }

    private static void report(ValidationContext vCxt, ShapeMapElement entry, Node focusNode,
                               ShexStatus result, String reason) {
        vCxt.shexReport(entry, focusNode, result, reason);
    }

    private static void track(Node shapeExprLabel, Node focus) {
    }
}
