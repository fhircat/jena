package org.apache.jena.shex.manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class Manifest<T extends ManifestEntry> {
    List<T> entries = new ArrayList<T>();

    abstract public T newEntry (Map<String, SourcedString> nvps);

    public List<T> getEntries() {
        return entries;
    }

    public void setEntries(List<T> entries) {
        this.entries = entries;
    }

    public void addEntry(T entry) {
        entries.add(entry);
    }

    public void clearEntries() {
        entries.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Manifest)) return false;
        Manifest<?> manifest = (Manifest<?>) o;
        return getEntries().equals(manifest.getEntries());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEntries());
    }
}
