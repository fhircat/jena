package org.apache.jena.shex.validation;

import org.apache.jena.shex.expressions.TripleExpr;

import java.util.*;

public class TripleExprSet<E extends TripleExpr> implements Set<E> {

    private final Map<Integer, E> map = new HashMap<>();

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
        return (o instanceof TripleExpr) && map.containsKey(((TripleExpr) o).id);
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
        if (! (o instanceof TripleExpr))
            return false;
        TripleExpr old = map.remove(((TripleExpr) o).id);
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
}
