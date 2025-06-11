package org.apache.jena.shex.reporting;

import org.apache.jena.shex.ShexStatus;

public class SimpleReport implements Report {

    private String message = "";
    private ShexStatus status = null;

    // TODO exchange order of parameters
    public SimpleReport(ShexStatus status, String message) {
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

    protected SimpleReport() {}
    protected void setMessage(String message) {
        this.message = message;
    }
    protected void setStatus(ShexStatus status) {
        this.status = status;
    }
}
