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

import java.util.Objects;

public class ShapeNot extends ShapeExpr {

    public static ShapeExpr create (ShapeExpr subExpr) {
        return new ShapeNot(subExpr);
    }

    private final ShapeExpr shapeExpr;

    private ShapeNot(ShapeExpr shapeExpr) {
        super();
        this.shapeExpr = shapeExpr;
    }

    public ShapeExpr getShapeExpr() {
        return shapeExpr;
    }

    @Override
    public void visit(VoidShapeExprVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <R> R visit(TypedShapeExprVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shapeExpr);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        ShapeNot other = (ShapeNot)obj;
        return Objects.equals(this.shapeExpr, other.shapeExpr);
    }
}
