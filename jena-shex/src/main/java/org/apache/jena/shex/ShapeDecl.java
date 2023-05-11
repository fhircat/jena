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

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.out.NodeFormatter;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.sys.SysShex;
import org.apache.jena.shex.sys.ValidationContext;

/** A labelled shape expression. */
public class ShapeDecl {
    private final Node label;
    private ShapeExpr shExpression;

    // [shex] Future : builder.
    public ShapeDecl(Node label, ShapeExpr shExpression) {
        this.label = label;
        this.shExpression = shExpression;
    }

    public Node getLabel() {
        return label;
    }

    public ShapeExpr getShapeExpression() {
        return shExpression;
    }

    public boolean satisfies(ValidationContext vCxt, Node data) {
        vCxt.startValidate(this, data);
        try {
            return shExpression == null
                ? true
                : shExpression.satisfies(vCxt, data) &&
                    shExpression.testShapeExprSemanticActions(vCxt, data);
        } finally {
            vCxt.finishValidate(this, data);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + ((shExpression == null) ? 0 : shExpression.hashCode());
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
        if ( shExpression == null ) {
            if ( other.shExpression != null )
                return false;
        } else if ( !shExpression.equals(other.shExpression) )
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ShexShape [label="+label+" expr="+shExpression+"]";
    }
}
