package org.apache.jena.shex.reporting;

import org.apache.jena.shex.ShexStatus;

public interface Report {
    String getMessage();
    ShexStatus getStatus();
}
