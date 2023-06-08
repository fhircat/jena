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

public class TripleExprCardinality extends TripleExpr {

    private final TripleExpr subExpr;
    private final Cardinality cardinality;

    public static TripleExprCardinality create (TripleExpr subExpr, Cardinality cardinality, List<SemAct> semActs) {
        return new TripleExprCardinality(subExpr, cardinality, semActs);
    }

    // TODO can this have semantic actions ?
    private TripleExprCardinality(TripleExpr subExpr, Cardinality cardinality, List<SemAct> semActs) {
        super(semActs);
        this.subExpr = subExpr;
        this.cardinality = cardinality;
    }

    public TripleExpr getSubExpr() { return subExpr; }
    public Cardinality getCardinality() { return cardinality; }

    public String cardinalityString() {
        return cardinality.getParsedFrom();
    }

    public int min() {
        return cardinality.min;
    }

    public int max() {
        return cardinality.max;
    }


    @Override
    public void visit(VoidTripleExprVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <R> R visit(TypedTripleExprVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subExpr);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        TripleExprCardinality other = (TripleExprCardinality)obj;
        return Objects.equals(this.subExpr, other.subExpr);
    }

}
