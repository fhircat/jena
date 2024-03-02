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

package org.apache.jena.shex.expressions;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.calc.TypedShapeExprVisitor;
import org.apache.jena.shex.calc.VoidShapeExprVisitor;

import java.util.Objects;

/** Shape expression that redirects. */
public class ShapeExprRef extends ShapeExpr {
    private final Node label;

    public static ShapeExprRef create(Node label) {
        return new ShapeExprRef(label);
    }

    private ShapeExprRef(Node ref) {
        super();
        this.label = ref;
    }

    public Node getLabel() { return label; }

    @Override
    public void visit(VoidShapeExprVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <R,A> R visit(TypedShapeExprVisitor<R,A> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        ShapeExprRef other = (ShapeExprRef)obj;
        return Objects.equals(label, other.label);
    }
}
