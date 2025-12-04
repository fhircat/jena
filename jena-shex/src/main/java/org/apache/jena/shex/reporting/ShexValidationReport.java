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
package org.apache.jena.shex.reporting;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeMapElement;
import org.apache.jena.shex.ShexStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShexValidationReport {

    private final List<ShapeMapElement> mapElementsReports = new ArrayList<>();

    public void forEachReport(Consumer<ShapeMapElement> action) {
        mapElementsReports.forEach(action);
    }

    public boolean conforms() {
        return mapElementsReports.stream().allMatch(it -> it.getStatus() == ShexStatus.conformant);
    }

    public void setReport (ShapeMapElement shapeMapElement, Report report) {
        mapElementsReports.add(
                new ShapeMapElement(shapeMapElement.nodeSelector, shapeMapElement.shapeExprLabel).createReportElement(report));
    }

    public void setStartSemanticActionReport (Report report) {
        mapElementsReports.add(
                new ShapeMapElement((Node) null, null).createReportElement(report));
    }

}
