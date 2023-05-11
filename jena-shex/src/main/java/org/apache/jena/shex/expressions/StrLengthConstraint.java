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

public class StrLengthConstraint extends NodeConstraintComponent {

    private final StrLengthKind lengthType;
    private final int length;

    public static StrLengthConstraint create(StrLengthKind lengthType, int len) {
        return new StrLengthConstraint(lengthType, len);
    }

    private StrLengthConstraint(StrLengthKind lengthType, int len) {
        Objects.requireNonNull(lengthType);
        this.lengthType = lengthType;
        this.length = len;
    }

    public StrLengthKind getLengthType() {
        return lengthType;
    }

    public int getLength() {
        return length;
    }

    @Override
    public void visit(NodeConstraintComponentVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StrLengthConstraint.class.getSimpleName() + "[", "]")
                .add("lengthType=" + lengthType.label())
                .add("length=" + length)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(length, lengthType);
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        StrLengthConstraint other = (StrLengthConstraint)obj;
        return length == other.length && lengthType == other.lengthType;
    }
}
