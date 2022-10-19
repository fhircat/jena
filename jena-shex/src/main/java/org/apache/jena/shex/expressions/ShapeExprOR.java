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

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.out.NodeFormatter;
import org.apache.jena.shex.sys.ReportItem;
import org.apache.jena.shex.sys.ValidationContext;

public class ShapeExprOR extends ShapeExpression {

    public static ShapeExpression create(List<ShapeExpression> acc) {
        if ( acc.size() == 0 )
            throw new InternalErrorException("Empty list");
        if ( acc.size() == 1 )
            return acc.get(0);
        return new ShapeExprOR(acc);
    }

    private List<ShapeExpression> shapeExpressions;

    private ShapeExprOR(List<ShapeExpression> expressions) {
        super(null);
        this.shapeExpressions = expressions;
    }

    public List<ShapeExpression> expressions() {
        return shapeExpressions;
    }


    @Override
    public boolean satisfies(ValidationContext vCxt, Node data) {
        // We need to ignore validation failures from expressions - we need to find one success.
        for ( ShapeExpression shExpr : shapeExpressions ) {
            ValidationContext vCxt2 = ValidationContext.create(vCxt);
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
    public void print(IndentedWriter out, NodeFormatter nFmt) {
        out.println("OR");
        //out.printf("OR(%d)\n", shapeExpressions.size());
        int idx = 0;
        for ( ShapeExpression shExpr : shapeExpressions ) {
            idx++;
            out.printf("%d -", idx);
            out.incIndent(4);
            shExpr.print(out, nFmt);
            out.decIndent(4);
        }
        out.println("/OR");
    }

    @Override
    public String toString() {
        return "ShapeExprOr "+expressions();
    }

    @Override
    public int hashCode() {
        return Objects.hash(2, shapeExpressions);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        ShapeExprOR other = (ShapeExprOR)obj;
        return Objects.equals(shapeExpressions, other.shapeExpressions);
    }
}
