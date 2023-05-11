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
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.sys.ValidationContext;

/** A labelled shape expression. */
public class ShapeDecl {
    private final Node label;
    private ShapeExpr shapeExpr;

    // [shex] Future : builder.
    public ShapeDecl(Node label, ShapeExpr shapeExpr) {
        this.label = label;
        this.shapeExpr = shapeExpr;
    }

    public Node getLabel() {
        return label;
    }

    public ShapeExpr getShapeExpr() {
        return shapeExpr;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + ((shapeExpr == null) ? 0 : shapeExpr.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        ShapeDecl other = (ShapeDecl)obj;
        if ( label == null ) {
            if ( other.label != null )
                return false;
        } else if ( !label.equals(other.label) )
            return false;
        if ( shapeExpr == null ) {
            if ( other.shapeExpr != null )
                return false;
        } else if ( !shapeExpr.equals(other.shapeExpr) )
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ShexShape [label="+label+" expr="+ shapeExpr +"]";
    }
}
