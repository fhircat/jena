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
import org.apache.jena.shex.calc.TypedTripleExprVisitor;
import org.apache.jena.shex.calc.VoidTripleExprVisitor;

import java.util.Objects;

public class TripleExprRef extends TripleExpr {

    private final Node label;

    public static TripleExprRef create (Node label) {
        return new TripleExprRef(label);
    }

    private TripleExprRef(Node label) {
        super(null);
        this.label = label;
    }

    public Node getLabel() {
        return label;
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
        TripleExprRef other = (TripleExprRef)obj;
        return Objects.equals(label, other.label);
    }

}
