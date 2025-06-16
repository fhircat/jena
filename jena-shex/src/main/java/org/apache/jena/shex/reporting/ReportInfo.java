package org.apache.jena.shex.reporting;

public class ReportInfo {

    private String message = "";
    private Object details = null;
    private boolean isErrorInfo;

    public ReportInfo(String message, Object details, boolean isErrorInfo) {
        this.message = message;
        this.details = details;
        this.isErrorInfo = isErrorInfo;
    }

    public String getMessage() { return message; }
    public Object getDetails() { return details; }
    public boolean isErrorInfo() { return isErrorInfo; }

    @Override
    public String toString() {
        String ds = details == null ? "" : String.format(" : %s", details);
        return message + ds;
    }
}
