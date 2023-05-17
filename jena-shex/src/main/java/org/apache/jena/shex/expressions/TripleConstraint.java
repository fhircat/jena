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

import org.apache.jena.graph.Node;

public class TripleConstraint extends TripleExpr {

    // TODO why triple constraint has a label while no other triple expression has, neither have shape expressions
    private final Node label;
    private final Node predicate;
    private final ShapeExpr valueExpr;
    private final boolean inverse;

    public static TripleConstraint create (Node label, Node predicate, boolean inverse,
                                    ShapeExpr valueExpr, List<SemAct> semActs) {
        return new TripleConstraint(label, predicate, inverse, valueExpr, semActs);
    }

    private TripleConstraint(Node label, Node predicate, boolean inverse, ShapeExpr valueExpr,  List<SemAct> semActs) {
        super(semActs);
        this.label = label;
        this.predicate = predicate;
        this.inverse = inverse;
        this.valueExpr = valueExpr;
    }

    public Node getLabel() {
        return label;
    }

    public Node getPredicate() {
        return predicate;
    }

    public ShapeExpr getValueExpr() {
        return valueExpr;
    }

    public boolean isInverse() {
        return inverse;
    }

    @Override
    public void visit(VoidTripleExprVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <R> R visit(TypedTripleExprVisitor<R> visitor) {
        return visitor.visit(this);
    }

//
//    @Override
//    public int hashCode() {
//        return Objects.hash(predicate, reverse, shapeExpr);
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if ( this == obj )
//            return true;
//        if ( obj == null )
//            return false;
//        if ( getClass() != obj.getClass() )
//            return false;
//        TripleConstraint other = (TripleConstraint)obj;
//        return /*max == other.max && min == other.min && */Objects.equals(predicate, other.predicate) && reverse == other.reverse
//               && Objects.equals(shapeExpr, other.shapeExpr);
//    }


    // FIXME incompatible hashCode and equals
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        TripleConstraint other = (TripleConstraint)obj;
        if (!this.predicate.equals(other.predicate) || !this.inverse == other.inverse)
            return false;
        if ( valueExpr == null ) {
            if ( other.valueExpr != null )
                return false;
        } else if ( !valueExpr.equals(other.valueExpr) )
            return false;
        return true;
    }
}
