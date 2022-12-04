package org.apache.jena.shex.manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class Manifest<T extends ManifestEntry> {
    List<T> entries = new ArrayList<T>();

    abstract public T newEntry ();

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
}
