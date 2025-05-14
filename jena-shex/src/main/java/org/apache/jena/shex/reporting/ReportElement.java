package org.apache.jena.shex.reporting;

import org.apache.jena.shex.ShexStatus;

public interface ReportElement {
    String getMessage();
    ShexStatus getStatus();
}
