package com.payresolve.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for demo credentials and role seeding.
 */
@Component
@ConfigurationProperties(prefix = "payresolve.auth")
public class AuthProperties {

    private String operatorUsername = "operator";
    private String operatorPassword = "operator123";
    private String viewerUsername = "viewer";
    private String viewerPassword = "viewer123";

    public String getOperatorUsername() {
        return operatorUsername != null && !operatorUsername.isBlank() ? operatorUsername : "operator";
    }

    public void setOperatorUsername(String operatorUsername) {
        this.operatorUsername = operatorUsername;
    }

    public String getOperatorPassword() {
        return operatorPassword != null && !operatorPassword.isBlank() ? operatorPassword : "operator123";
    }

    public void setOperatorPassword(String operatorPassword) {
        this.operatorPassword = operatorPassword;
    }

    public String getViewerUsername() {
        return viewerUsername != null && !viewerUsername.isBlank() ? viewerUsername : "viewer";
    }

    public void setViewerUsername(String viewerUsername) {
        this.viewerUsername = viewerUsername;
    }

    public String getViewerPassword() {
        return viewerPassword != null && !viewerPassword.isBlank() ? viewerPassword : "viewer123";
    }

    public void setViewerPassword(String viewerPassword) {
        this.viewerPassword = viewerPassword;
    }
}

