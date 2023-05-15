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

import org.apache.jena.graph.Triple;
import org.apache.jena.shex.eval.TripleExprVisitor2;
import org.apache.jena.shex.sys.ValidationContext;

import java.util.List;
import java.util.Set;

public abstract class TripleExpr {

    private List<SemAct> semActs;

    protected TripleExpr(List<SemAct> semActs) {
        this.semActs = semActs;
    }

    public List<SemAct> getSemActs() {
        return semActs;
    }

    public void setSemActs(List<SemAct> semActs) { // needed for ShExC parser's late binding of SemActs to EachOf
        this.semActs = semActs;
    }

    public boolean testSemanticActions(ValidationContext v, Set<Triple> matchables) {
        if (this.semActs == null)
            return true;
        return v.dispatchTripleExprSemanticAction(this, matchables);
    }

    public abstract void visit(TripleExprVisitor visitor);

    public abstract <R> R visit(TripleExprVisitor2<R> visitor);

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public String toString() {
        return PrettyPrinter.asPrettyString(this);
    }
}
