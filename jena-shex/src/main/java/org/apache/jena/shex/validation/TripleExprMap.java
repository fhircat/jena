package org.apache.jena.shex.validation;

import org.apache.jena.shex.expressions.TripleExpr;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TripleExprMap<K extends TripleExpr, V> implements Map<TripleExpr, V> {

    private final Map<Integer, V> map = new HashMap<>();

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
        return (key instanceof TripleExpr) && map.containsKey(((TripleExpr) key).id);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        if (! (key instanceof TripleExpr))
            return null;
        return map.get(((TripleExpr)key).id);
    }

    @Override
    public V put(TripleExpr key, V value) {
        return map.put(key.id, value);
    }

    @Override
    public V remove(Object key) {
        if (! (key instanceof TripleExpr))
            return null;
        return map.remove(((TripleExpr)key).id);
    }

    @Override
    public void putAll(Map<? extends TripleExpr, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<TripleExpr> keySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<Entry<TripleExpr, V>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
