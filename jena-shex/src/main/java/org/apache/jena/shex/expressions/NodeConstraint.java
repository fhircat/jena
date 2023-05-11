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

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.out.NodeFormatter;
import org.apache.jena.shex.sys.ReportItem;
import org.apache.jena.shex.sys.ValidationContext;

public class NodeConstraint extends ShapeExpr {

    /*
    NodeConstraint  {
        id:shapeExprLabel?
        nodeKind:("iri" | "bnode" | "nonliteral" | "literal")?
        datatype:IRIREF?
        xsFacet*
        values:[valueSetValue+]?
    }
     */

    private final List<NodeConstraintComponent> components;

    public static NodeConstraint create (List<NodeConstraintComponent> components, List<SemAct> semActs) {
        return new NodeConstraint(components, semActs);
    }

    // TODO can node constraints have semantic actions ?
    // TODO why is the list of components copied, while no other lists are copied elsewhere in expressions (eg sub-expressions of a ShapeAnd are not copied)
    private NodeConstraint(List<NodeConstraintComponent> components, List<SemAct> semActs) {
        super(semActs);
        this.components = List.copyOf(components);
    }

    public List<NodeConstraintComponent> getComponents() { return components; }

    @Override
    public void print(IndentedWriter out, NodeFormatter nFmt) {
        out.println(toString());
    }

    @Override
    public void visit(ShapeExprVisitor visitor) {
        visitor.visit(this);
    }


    @Override
    public boolean satisfies(ValidationContext vCxt, Node data) {
        for ( NodeConstraintComponent ncc : components) {
            ReportItem item = ncc.nodeSatisfies(vCxt, data);
            if ( item != null ) {
                vCxt.reportEntry(item);
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((components == null) ? 0 : components.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        NodeConstraint other = (NodeConstraint)obj;
        if ( components == null ) {
            if ( other.components != null )
                return false;
        } else if ( !components.equals(other.components) )
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "NodeConstraint [constraints=" + components + "]";
    }
}