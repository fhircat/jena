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
import java.util.StringJoiner;

import org.apache.jena.graph.Node;
import org.apache.jena.riot.out.NodeFmtLib;
import org.apache.jena.shex.ShexException;
import org.apache.jena.shex.calc.TypedNodeConstraintComponentVisitor;
import org.apache.jena.shex.calc.VoidNodeConstraintComponentVisitor;
import org.apache.jena.sparql.expr.NodeValue;

public class NumRangeConstraint extends NodeConstraintComponent {

    private final NumRangeKind rangeKind;
    private final Node value;
    private final NodeValue numericValue;

    public NumRangeConstraint(NumRangeKind rangeKind, Node value) {
        Objects.requireNonNull(rangeKind);
        this.rangeKind = rangeKind;
        this.value = value;
        NodeValue nv = NodeValue.makeNode(value);
        if ( ! nv.isNumber() )
            throw new ShexException("Not a number: "+value);
        this.numericValue = nv;
    }

    public NumRangeKind getRangeKind() {
        return rangeKind;
    }

    public Node getValue() {
        return value;
    }

    public NodeValue getNumericValue() {
        return numericValue;
    }

    @Override
    public void visit(VoidNodeConstraintComponentVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <R> R visit(TypedNodeConstraintComponentVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numericValue, rangeKind, value);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        NumRangeConstraint other = (NumRangeConstraint)obj;
        return Objects.equals(numericValue, other.numericValue) && rangeKind == other.rangeKind && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        // TODO uses NodeFmtLib, why not our internal pretty printer ?
        return new StringJoiner(", ", NumRangeConstraint.class.getSimpleName() + "[", "]")
                .add("rangeKind=" + rangeKind.label())
                .add("value=" + NodeFmtLib.displayStr(value))
                .add("numericValue=" + numericValue)
                .toString();
    }

}
