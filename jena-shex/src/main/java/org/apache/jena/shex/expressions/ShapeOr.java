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
import java.util.StringJoiner;

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.out.NodeFormatter;
import org.apache.jena.shex.sys.ReportItem;
import org.apache.jena.shex.sys.ValidationContext;

public class ShapeOr extends ShapeExpr {

    public static ShapeExpr create(List<ShapeExpr> subExprs) {
        if ( subExprs.size() == 0 )
            throw new InternalErrorException("Empty list");
        if ( subExprs.size() == 1 )
            return subExprs.get(0);
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
    public boolean satisfies(ValidationContext vCxt, Node data) {
        // We need to ignore validation failures from expressions - we need to find one success.
        for ( ShapeExpr shExpr : shapeExprs) {
            ValidationContext vCxt2 = vCxt.create();
            boolean innerSatisfies = shExpr.satisfies(vCxt2, data);
            if ( innerSatisfies )
                return true;
        }
        ReportItem item = new ReportItem("OR expression not satisfied:", data);
        vCxt.reportEntry(item);
        return false;
    }

    @Override
    public void visit(ShapeExprVisitor visitor) {
        visitor.visit(this);
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
