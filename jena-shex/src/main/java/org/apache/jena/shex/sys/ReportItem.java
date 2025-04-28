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

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.expressions.Expression;

import java.util.Set;

/**
 * Information about validation of an expression on a node.
 */
/* TODO not satisfactory. In this version does not allow to create items for node constraint components, but
    adding a third possibility (already ShapeDecl and Expression) would be too much
    Should possibly be an interface with different implementations.  */
public class ReportItem {
    private final String message;
    private final Node node;
    private Set<Triple> neigh;
    private Expression expr;
    private ShapeDecl shapeDecl; // TODO needed for reporting on checking subtypes
    // TODO a level of reporting might be considered

    // TODO shoud be package visibility, the ReportItem and ShexReport should be in the same package
    public ReportItem(String message, Node node, Set<Triple> subNeighbourhood,
                      Expression expression, ShapeDecl shapeDecl) {
        this.message = message;
        this.node = node;
        this.neigh = subNeighbourhood;
        this.expr = expression;
        this.shapeDecl = shapeDecl;
    }

    public String getMessage() {
        return message;
    }

    public Node getNode() {
        return node;
    }

    @Override
    public String toString() {
        return message+" ("+ShexLib.displayStr(node)+")";
    }
}
