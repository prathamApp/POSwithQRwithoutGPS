package com.example.pef.prathamopenschool.ftpSettings.hotspot_android;

public class ConnectionResult {
    private String message;
    private boolean successful;

    public ConnectionResult(String message, boolean successful) {
        this.message = message;
        this.successful = successful;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }
}