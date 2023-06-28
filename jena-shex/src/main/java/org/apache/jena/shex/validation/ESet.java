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
package org.apache.jena.shex.validation;

import org.apache.jena.shex.expressions.Expression;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class ESet<E extends Expression> implements Set<E> {

    private final Map<Integer, E> map = new LinkedHashMap<>();

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return (o instanceof Expression) && map.containsKey(((Expression) o).id);
    }

    @Override
    public Iterator<E> iterator() {
        return map.values().iterator();
    }

    @Override
    public Object[] toArray() {
        return map.values().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public boolean add(E tripleExpr) {
        E old = map.putIfAbsent(tripleExpr.id, tripleExpr);
        return old != null;
    }

    @Override
    public boolean remove(Object o) {
        if (! (o instanceof Expression))
            return false;
        Expression old = map.remove(((Expression) o).id);
        return old != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return c.stream().allMatch(this::contains);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c)
            changed |= add(e);
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return c.stream().anyMatch(this::remove);
    }

    @Override
    public void clear() {
        map.clear();
    }

    public static <E extends Expression> Collector<E, ESet<E>, ESet<E>> collector () {
        return new Collector<>() {
            @Override
            public Supplier<ESet<E>> supplier() {
                return ESet::new;
            }

            @Override
            public BiConsumer<ESet<E>, E> accumulator() {
                return ESet::add;
            }

            @Override
            public BinaryOperator<ESet<E>> combiner() {
                return (x1, x2) -> {
                    ESet<E> result = new ESet<>();
                    result.addAll(x1);
                    result.addAll(x2);
                    return result;
                };
            }

            @Override
            public Function<ESet<E>, ESet<E>> finisher() {
                return Function.identity();
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Set.of(Characteristics.IDENTITY_FINISH);
            }
        };
    }
}
