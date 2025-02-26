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

package org.apache.jena.shex;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.sys.ShexLib;
import org.apache.jena.shex.sys.SysShex;

/**
 * {@code ShexShapeRecord} is an entry in a {@linkplain ShapeMap} used for both targeting shapes and reporting violations.
 */
public class ShapeMapElement {
//  nodeSelector / patternSelector : an RDF node, or a triple pattern which is used to select RDF nodes.
//  focusSelector :
//  shape: ShEx shapeExprLabel or the string "START" for the start shape expression.
//  status: [default="conformant"] "nonconformant" or "conformant".
//  reason: [optional] a string stating a reason for failure or success.
//  appInfo: [optional] an application-specific JSON-LD structure

    public final Node nodeSelector;
    public final Triple patternSelector;
    public final Node focusSelector;
    public final Node shapeExprLabel;
    public final ShexStatus status;
    public final String reason;

//  public final String appInfo;

    public ShapeMapElement(Node node, Node shapeExprLabel) {
        this(node, null, shapeExprLabel);
    }

    public ShapeMapElement(Triple patternSelector, Node shapeExprLabel) {
        this(null, patternSelector, shapeExprLabel);
        // Check triples for "FOCUS"
        if ( !isSubjectFocus() && ! isObjectFocus() )
            throw new ShexException("Triple pattern must have either subject or object as FOCUS");
        if ( isSubjectFocus() && isObjectFocus() )
            throw new ShexException("Triple pattern must one one of subject or object as FOCUS");
    }

    private ShapeMapElement(Node node, Triple patternSelector, Node shapeExprLabel) {
        this(node, patternSelector, shapeExprLabel, null, null, null);
    }

    public ShapeMapElement(ShapeMapElement assoc, Node focusNode, ShexStatus status, String reason) {
        // Reporting form.
        this(assoc.nodeSelector, assoc.patternSelector, assoc.shapeExprLabel, focusNode, status, reason);
    }

    private ShapeMapElement(Node node, Triple pattern, Node shapeExprLabel, Node focusNode, ShexStatus status, String reason) {
        super();
        this.nodeSelector = node;
        this.patternSelector = pattern;
        this.shapeExprLabel = shapeExprLabel;
        this.status = status;
        this.focusSelector = focusNode;
        this.reason = reason;
    }

    public boolean isSubjectFocus() {
        return patternSelector != null && SysShex.focusNode.equals(patternSelector.getSubject());
    }

    public boolean isObjectFocus() {
        return patternSelector != null && SysShex.focusNode.equals(patternSelector.getObject());
    }

    public Triple asMatcher() {
        if ( patternSelector == null )
            return null;
        return Triple.create(n(patternSelector.getSubject()),
                             n(patternSelector.getPredicate()),
                             n(patternSelector.getObject()));
    }

    private Node n(Node node) {
        return ( node == null || node.isExt() ) ? Node.ANY : node ;
    }

    @Override
    public String toString() {
        StringBuilder sBuff = new StringBuilder();
        String str = strTarget();
        if ( focusSelector != null )
            str = str+" "+ShexLib.displayStr(focusSelector);
        if ( status != null )
            str = str+" "+status;
        if ( reason != null )
            str = str+" :: "+reason;
        return str;
    }

    public String strTarget() {
        if ( patternSelector != null ) {
            return String.format("{ %s %s %s } @ %s",
                                 str(patternSelector.getSubject()), str(patternSelector.getPredicate()), str(patternSelector.getObject()),
                                 str(shapeExprLabel));
        }
        if ( nodeSelector != null ) {
            return String.format("%s @ %s", str(nodeSelector), str(shapeExprLabel));
        }
        return "ShexShapeAssociation/null";
    }

    private static String str(Node x) {
        if ( x == null || x.equals(Node.ANY) )
            return "_";
        if ( x == SysShex.focusNode )
            return "FOCUS";
        if ( x == SysShex.startNode )
            return "START";
        return ShexLib.displayStr(x);
    }
}
