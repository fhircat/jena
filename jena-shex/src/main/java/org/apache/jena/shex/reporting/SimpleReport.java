package org.apache.jena.shex.reporting;

import org.apache.jena.shex.ShexStatus;

public class SimpleReport implements Report {

    private String message = "";
    private ShexStatus status = null;
    private Object details = null;

    public SimpleReport(ShexStatus status, String message, Object details) {
        this.message = message;
        this.status = status;
        this.details = details;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ShexStatus getStatus() {
        return status;
    }

    public Object getDetails() { return details; }

    protected SimpleReport() {}
    protected void setMessage(String message) {
        this.message = message;
    }
    protected void setStatus(ShexStatus status) {
        this.status = status;
    }
    protected void setDetails(Object details) {
        this.details = details;
    }

    @Override
    public String toString() {
        String ds = details == null ? "" : String.format(" : %s", details);
        return message + ds;
    }
}
