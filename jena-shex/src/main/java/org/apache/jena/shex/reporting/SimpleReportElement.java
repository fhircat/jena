package org.apache.jena.shex.reporting;

import org.apache.jena.shex.ShexStatus;

public class SimpleReportElement implements ReportElement {

    private String message = "";
    private ShexStatus status = null;

    // TODO exchange order of parameters
    public SimpleReportElement(ShexStatus status, String message) {
        this.message = message;
        this.status = status;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ShexStatus getStatus() {
        return status;
    }

    protected SimpleReportElement() {}
    protected void setMessage(String message) {
        this.message = message;
    }
    protected void setStatus(ShexStatus status) {
        this.status = status;
    }
}
