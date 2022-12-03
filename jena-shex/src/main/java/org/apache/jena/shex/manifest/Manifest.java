package org.apache.jena.shex.manifest;

import java.util.ArrayList;
import java.util.List;

abstract class Manifest<T extends ManifestEntry> {
    List<T> entries = new ArrayList<T>();

    abstract public T newEntry ();

    public List<T> getEntries() {
        return entries;
    }

    public void setEntries(List<T> entries) {
        this.entries = entries;
    }
}
