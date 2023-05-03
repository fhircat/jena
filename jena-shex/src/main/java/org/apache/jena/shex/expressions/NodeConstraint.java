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
import org.apache.jena.graph.Node;
import org.apache.jena.riot.out.NodeFormatter;
import org.apache.jena.shex.sys.ValidationContext;

/** A node constraint (nonLitNodeConstraint or litNodeConstraint) in a shape atom.
<pre>
ShapeAtom := ( nonLitNodeConstraint ( inlineShapeOrRef )?
             | litNodeConstraint
             | inlineShapeOrRef ( nonLitNodeConstraint )?
             | &lt;LPAREN&gt; shapeExpression &lt;RPAREN&gt;
             | &lt;DOT&gt;
             )
</pre>
*/
public class NodeConstraint extends ShapeExpr {

    private final NodeConstraintProxy nodeConstraintProxy;

    public NodeConstraint(NodeConstraintProxy nodeConstraintProxy, List<SemAct> semActs) {
        this(null, Objects.requireNonNull(nodeConstraintProxy, "NodeConstraint"), semActs);
    }

    private NodeConstraint(ShapeExpr shapeExpr, NodeConstraintProxy nodeConstraintProxy, List<SemAct> semActs) {
        super(semActs);
        this.nodeConstraintProxy = nodeConstraintProxy;

    }

    public NodeConstraintProxy getNodeConstraint() {
        return nodeConstraintProxy;
    }

    @Override
    public void print(IndentedWriter out, NodeFormatter nFmt) {
        out.println(toString());
    }

    @Override
    public boolean satisfies(ValidationContext vCxt, Node data) {
        return nodeConstraintProxy.satisfies(vCxt, data);
    }

    @Override
    public void visit(ShapeExprVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return 1+Objects.hash(nodeConstraintProxy);
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
        return Objects.equals(nodeConstraintProxy, other.nodeConstraintProxy);
    }

    @Override
    public String toString() {
        if ( nodeConstraintProxy != null )
            return "ShapeNodeConstraint [ "+ nodeConstraintProxy +" ]";
        return "ShapeNodeConstraint []";
    }
}
