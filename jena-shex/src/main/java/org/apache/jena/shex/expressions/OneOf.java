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
import org.apache.jena.riot.out.NodeFormatter;

public class OneOf extends TripleExpr {

    public static TripleExpr create(List<TripleExpr> subExprs, List<SemAct> semActs) {
        if ( subExprs.size() == 0 )
            throw new InternalErrorException("Empty list");
        if ( subExprs.size() == 1 )
            return subExprs.get(0);
        return new OneOf(subExprs, semActs);
    }

    private List<TripleExpr> tripleExprs;

    private OneOf(List<TripleExpr> subExprs, List<SemAct> semActs) {
        super(semActs);
        this.tripleExprs = subExprs;
    }

    public List<TripleExpr> getTripleExprs() {
        return tripleExprs;
    }

    @Override
    public void visit(TripleExprVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(4, tripleExprs);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        OneOf other = (OneOf)obj;
        return Objects.equals(tripleExprs, other.tripleExprs);
    }

    @Override
    public void print(IndentedWriter iOut, NodeFormatter nFmt) {
//        iOut.println("OneOf");
//        iOut.incIndent();
//        expressions().forEach(tExpr->tExpr.print(iOut, nFmt));
//        iOut.decIndent();
//        iOut.println("/OneOf");

        iOut.println("OneOf");
        iOut.incIndent();
        int idx = 0;
        for ( TripleExpr tExpr : tripleExprs) {
            idx++;
            iOut.printf("%d - ", idx);
            tExpr.print(iOut, nFmt);
        }
        iOut.decIndent();
        iOut.println("/OneOf");
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()+"["+ tripleExprs +"]";
    }
}
