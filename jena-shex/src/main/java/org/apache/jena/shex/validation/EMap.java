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
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class EMap<K extends Expression, V> implements Map<K, V> {

    private final Map<Integer, Map.Entry<K,V>> map = new LinkedHashMap<>();

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return (key instanceof Expression) && map.containsKey(((Expression) key).id);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.values().stream().anyMatch(e -> e.getValue().equals(value));
    }

    @Override
    public V get(Object key) {
        if (! (key instanceof Expression))
            return null;
        Map.Entry<K,V> pair = map.get(((Expression)key).id);
        return pair == null ? null : pair.getValue();
    }

    @Override
    public V put(K key, V value) {
        Map.Entry<K,V> previous = map.put(key.id, new MyEntry<>(key,value));
        return previous == null ? null : previous.getValue();
    }

    @Override
    public V remove(Object key) {
        if (! (key instanceof Expression))
            return null;
        Map.Entry<K,V> previous = map.remove(((Expression)key).id);
        return previous == null ? null : previous.getValue();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        m.forEach(this::put);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<K> keySet() {
        return map.values().stream().map(Entry::getKey).collect(Collectors.toSet());
    }

    @Override
    public Collection<V> values() {
        return map.values().stream().map(Entry::getValue).collect(Collectors.toSet());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new HashSet<>(map.values());
    }

    public static <E, K extends Expression, V> Collector<E,?,EMap<K,V>> collector (
             Function<E, K> keyMapper,
             Function<E, V> valueMapper) {
        return Collectors.toMap(keyMapper, valueMapper,
                (v1, v2) -> { throw new IllegalStateException("key duplicates not allowed"); },
                EMap::new);
    }

    private static class MyEntry<K extends Expression,V> implements Map.Entry<K,V> {

        private final K key;
        private final V value;

        private MyEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MyEntry)) return false;

            MyEntry<?, ?> myEntry = (MyEntry<?, ?>) o;

            if (key.id != myEntry.key.id) return false;
            return value.equals(myEntry.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key.id, value);
        }
    }

}
