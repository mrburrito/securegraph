package com.altamiracorp.securegraph.util;

import java.util.Iterator;

public abstract class FilterIterable<T> extends LookAheadIterable<T, T> implements Iterable<T> {
    private final Iterable<T> iterable;

    public FilterIterable(Iterable<T> iterable) {
        this.iterable = iterable;
    }

    @Override
    protected T convert(T next) {
        return next;
    }

    @Override
    protected Iterator<T> createIterator() {
        return this.iterable.iterator();
    }

    protected abstract boolean isIncluded(T obj);
}