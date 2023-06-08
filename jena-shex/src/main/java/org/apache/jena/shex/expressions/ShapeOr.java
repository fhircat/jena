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

import java.util.List;
import java.util.Objects;

import org.apache.jena.atlas.lib.InternalErrorException;

public class ShapeOr extends ShapeExpr {

    public static ShapeOr create(List<ShapeExpr> subExprs) {
        if ( subExprs.size() < 2 )
            throw new InternalErrorException("ShapeOr requires two or more disjuncts");
        return new ShapeOr(subExprs);
    }

    private final List<ShapeExpr> shapeExprs;

    private ShapeOr(List<ShapeExpr> subExprs) {
        super();
        this.shapeExprs = subExprs;
    }

    public List<ShapeExpr> getShapeExprs() {
        return shapeExprs;
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
        return Objects.hash(2, shapeExprs);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        ShapeOr other = (ShapeOr)obj;
        return Objects.equals(shapeExprs, other.shapeExprs);
    }
}
