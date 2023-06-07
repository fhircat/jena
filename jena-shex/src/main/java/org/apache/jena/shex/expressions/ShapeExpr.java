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

// TODO are annotations allowed for shape expressions ? If yes, we should add them
public abstract class ShapeExpr {

    private final List<SemAct> semActs;

    public ShapeExpr(List<SemAct> semActs) {
        this.semActs = semActs;
    }

    protected ShapeExpr() { this(null); }

    public List<SemAct> getSemActs() {
        return semActs;
    }

    public abstract void visit(VoidShapeExprVisitor visitor);
    public abstract <R> R visit(TypedShapeExprVisitor<R> visitor);

    @Override
    public String toString() {
        return PrettyPrinter.asPrettyString(this);
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);
}
