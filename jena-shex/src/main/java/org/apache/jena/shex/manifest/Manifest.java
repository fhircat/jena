package org.apache.jena.shex.manifest;

import java.util.ArrayList;
import java.util.List;

public class Manifest {
    List<ValidationEntry> entries = new ArrayList<>();

    public ValidationEntry newEntry () {
        ValidationEntry ret = new ValidationEntry();
        entries.add(ret);
        return ret;
    }
}
