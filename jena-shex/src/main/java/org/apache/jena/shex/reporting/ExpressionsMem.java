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
