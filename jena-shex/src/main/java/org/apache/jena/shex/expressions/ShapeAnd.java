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
import org.apache.jena.shex.calc.TypedShapeExprVisitor;
import org.apache.jena.shex.calc.VoidShapeExprVisitor;

public class ShapeAnd extends ShapeExpr {

    // Could pull out ShapeExpressionN

    public static ShapeAnd create(List<ShapeExpr> subExprs) {
        if ( subExprs.size() < 2 )
            throw new InternalErrorException("ShapeAnd requires two or more conjuncts");
        return new ShapeAnd(subExprs);
    }

    private final List<ShapeExpr> shapeExprs;

    private ShapeAnd(List<ShapeExpr> subExprs) {
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
    public <R,A> R visit(TypedShapeExprVisitor<R,A> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(1, shapeExprs);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        ShapeAnd other = (ShapeAnd)obj;
        return Objects.equals(shapeExprs, other.shapeExprs);
    }
}
