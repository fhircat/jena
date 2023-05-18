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

import java.util.*;

import org.apache.jena.graph.Node;

// Shape
public class Shape extends ShapeExpr {
    // [shex] This is the inlineShapeDefinition
    // Can we combine with a top-level ShexShape?

    private Node label;
    private Set<Node> extras;
    private List<ShapeExprRef> xtends;
    private boolean closed;
    private TripleExpr tripleExpr;
    public static Builder newBuilder() { return new Builder(); }

    // TODO why a Shape has a label while the other shape expressions do not ?
    private Shape(Node label, Set<Node> extras, boolean closed, List<ShapeExprRef> xtends, TripleExpr tripleExpr, List<SemAct> semActs) {
        super(semActs);
        this.label = label;
        if ( extras == null || extras.isEmpty() )
            this.extras = null;
        else
            this.extras = extras;
        this.closed = closed;
        if ( xtends == null || xtends.isEmpty() )
            this.xtends = null;
        else
            this.xtends = xtends;
        this.tripleExpr = tripleExpr;
    }

    public TripleExpr getTripleExpr() { return tripleExpr; }

    public Node getLabel() {
        return label;
    }

    public Set<Node> getExtras() {
        return extras;
    }

    public boolean isClosed() {
        return closed;
    }

    public List<ShapeExprRef> getExtends() {
        return xtends;
    }

    @Override
    public void visit(ShapeExprVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Shape.class.getSimpleName() + "[", "]")
                .add("label=" + label)
                .add("extras=" + extras)
                .add("closed=" + closed)
                .add("extends=" + xtends)
                .add("tripleExpr=" + tripleExpr)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(closed, label, tripleExpr);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        Shape other = (Shape)obj;
        return closed == other.closed && Objects.equals(label, other.label) && tripleExpr.equals(other.tripleExpr);
    }

    public static class Builder {
        private Node label;
        private Set<Node> extras = null;
        private List<ShapeExprRef> xtends = null;
        private List<SemAct> semActs;
        private Optional<Boolean> closed = null;
        private TripleExpr tripleExpr = null;

        Builder() {}

        public Builder label(Node label) { this.label = label ; return this; }

        public Builder extras(List<Node> extrasList) {
            if ( extras == null )
                extras = new HashSet<>();
            this.extras.addAll(extrasList);
            return this;
        }

        public Builder xtends(List<ShapeExprRef> extendsList) {
            if ( xtends == null )
                xtends = new ArrayList<>();
            this.xtends.addAll(extendsList);
            return this;
        }

        public Builder semActs(List<SemAct> semActsList) {
            if ( semActs == null )
                semActs = new ArrayList<>();
            if (semActsList != null)
                this.semActs.addAll(semActsList);
            return this;
        }

        public Builder closed(boolean value) { this.closed = Optional.of(value); return this; }

        public Builder shapeExpr(TripleExpr tripleExpr) { this.tripleExpr = tripleExpr; return this; }

        public Shape build() {
            boolean isClosed = (closed == null) ? false : closed.get();
            return new Shape(label, extras, isClosed, xtends, tripleExpr, semActs);
        }
    }
}
