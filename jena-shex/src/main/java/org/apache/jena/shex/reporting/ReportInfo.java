package org.apache.jena.shex.reporting;

public class ReportInfo {

    private String message = "";
    private Object details = null;

    public ReportInfo(String message, Object details) {
        this.message = message;
        this.details = details;
    }

    public String getMessage() { return message; }
    public Object getDetails() { return details; }

    @Override
    public String toString() {
        String ds = details == null ? "" : String.format(" : %s", details);
        return message + ds;
    }
}
