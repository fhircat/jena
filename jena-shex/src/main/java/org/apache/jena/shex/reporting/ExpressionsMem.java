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
package org.apache.jena.shex.reporting;

import org.apache.jena.shex.expressions.Expression;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.TripleExpr;
import org.apache.jena.shex.validation.EMap;

import java.util.EnumMap;

/** Used to store static analysis information about triple and shape expressions. */
public class ExpressionsMem {

    private EMap<Expression, EnumMap<Properties, Object>> properties;

    public Boolean isDeterministic(Shape shape) {
        EnumMap<Properties, Object> props = properties.computeIfAbsent(shape, e -> new EnumMap<>(Properties.class));
        return (Boolean) props.computeIfAbsent(Properties.DETERMINISTIC, e -> null); // TODO actual computation of deterministic
    }

    public enum Properties {
        DETERMINISTIC(Shape.class, Boolean.class);


        final Class<? extends Expression> expressionType;
        final Class<?> propertyType;
        Properties(Class<? extends Expression> expressionType, Class<?> propertyType) {
            this.expressionType = expressionType;
            this.propertyType = propertyType;
        }

        }
}
