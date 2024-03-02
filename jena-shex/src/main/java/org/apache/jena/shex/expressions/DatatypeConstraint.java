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

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.shex.calc.TypedNodeConstraintComponentVisitor;
import org.apache.jena.shex.calc.VoidNodeConstraintComponentVisitor;
import org.apache.jena.vocabulary.XSD;

import java.util.Objects;

import static org.apache.jena.shex.sys.ShexLib.displayStr;

public class DatatypeConstraint extends NodeConstraintComponent {
    private final Node datatype;
    private final String dtURI;
    private final RDFDatatype rdfDatatype;

    public DatatypeConstraint(Node dt) {
        this( dt.isURI() ? dt.getURI() : null , Objects.requireNonNull(dt));
        if ( ! dt.isURI() )
            throw new IllegalArgumentException("Not a URI: "+dt);
    }

    public DatatypeConstraint(String dt) {
        this(Objects.requireNonNull(dt), NodeFactory.createURI(dt));
    }

    private DatatypeConstraint(String dtURI, Node dt) {
        this.datatype = dt;
        this.dtURI = dtURI;
        this.rdfDatatype = NodeFactory.getType(dtURI);
    }

    public Node getDatatype() {
        return datatype;
    }

    public String getDatatypeURI() {
        return dtURI;
    }

    public RDFDatatype getRDFDatatype() {
        return rdfDatatype;
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
    public String toString() {
        String className = DatatypeConstraint.class.getSimpleName();
        String x;
        if ( datatype.isURI() ) {
            if ( dtURI.startsWith(XSD.getURI()) )
                x = "xsd:"+datatype.getLocalName();
            else
                x = "<"+dtURI+">";
        } else if ( datatype.isBlank() )
            x = "<_:"+datatype.getBlankNodeLabel()+">";
        else
            x = displayStr(datatype);

        return String.format("%s[%s]", className, x);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datatype, dtURI);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( !(obj instanceof DatatypeConstraint) )
            return false;
        DatatypeConstraint other = (DatatypeConstraint)obj;
        return Objects.equals(datatype, other.datatype) && Objects.equals(dtURI, other.dtURI);
    }
}
