
package de.muenchen.mcmp.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Einfacher Service für SIEM-Logging im CEF Format
 */
@Slf4j
@Service
public class SiemLoggingService {

    private static final String CEF_VERSION = "0";
    private static final String DEVICE_VENDOR = "LHM";
    private static final String DEVICE_PRODUCT = "mCMP";
    private static final String DEVICE_VERSION = "1.0";

    private final String hostName;
    private static final org.slf4j.Logger SIEM_LOGGER = org.slf4j.LoggerFactory.getLogger("SIEM.CEF");

    public SiemLoggingService(@Value("${server.hostname:localhost}") String hostName) {
        this.hostName = hostName;
    }

    /**
     * Loggt Authentication Events
     */
    public void logAuthSuccess(String username, String remoteIp, Collection<SimpleGrantedAuthority> authorities, String details) {
        String signatureId = "100";
        String eventName = "Login Success";
        String message = String.format("User %s %s from %s", username, "logged in successfully", remoteIp);
        Map<String, String> additionalFields = new LinkedHashMap<>();
        additionalFields.put("act", "Authentication Success");
        additionalFields.put("outcome", "Success");
        if (authorities != null) {
            additionalFields.put("roles", authorities.toString());
        }
        if (details != null && !details.isBlank()) {
            additionalFields.put("details", details);
        }
        logCefEvent(signatureId, eventName, 0, username, remoteIp, message, additionalFields);
    }

    public void logAuthFailure(String username, String remoteIp, String error, String details) {
        String signatureId = "200";
        String eventName = "Login Failed";
        String message = String.format("User %s %s from %s", username, "login failed", remoteIp);
        Map<String, String> additionalFields = new LinkedHashMap<>();
        additionalFields.put("act", "Authentication Failure");
        additionalFields.put("outcome", "Failure");
        if (error != null) {
            additionalFields.put("reason", error);
        }
        if (details != null && !details.isBlank()) {
            additionalFields.put("details", details);
        }
        logCefEvent(signatureId, eventName, 7, username, remoteIp, message, additionalFields);
    }

    /**
     * Loggt Admin Privilege Events
     */
    public void logAdminAccess(String username, String remoteIp) {
        logCefEvent("400", "Admin Access Granted", 6, username, remoteIp, "User granted admin privileges", null);
    }



    /**
     * Loggt Security Errors
     */
    public void logSecurityError(String username, String remoteIp, String error) {
        logCefEvent("500", "Security Error", 8, username, remoteIp, error, null);
    }

    /**
     * Basis CEF Log Methode
     */
    private void logCefEvent(String signatureId, String eventName, int severity,
                             String username, String remoteIp, String message, Map<String, String> additionalFields) {
        final StringBuilder cef = new StringBuilder();

        // CEF Header: CEF:Version|Device Vendor|Device Product|Device Version|Signature ID|Name|Severity|Extension
        cef.append("CEF:")
                .append(CEF_VERSION).append("|")
                .append(DEVICE_VENDOR).append("|")
                .append(DEVICE_PRODUCT).append("|")
                .append(DEVICE_VERSION).append("|")
                .append(escapeCefField(signatureId)).append("|")
                .append(escapeCefField(eventName)).append("|")
                .append(severity).append("|");

        // CEF Extensions
        if (username != null) {
            cef.append("suser=").append(escapeCefExtension(username)).append(" ");
        }
        if (remoteIp != null) {
            cef.append("src=").append(escapeCefExtension(remoteIp)).append(" ");
        }
        if (message != null) {
            cef.append("msg=").append(escapeCefExtension(message)).append(" ");
        }

        cef.append("rt=").append(Instant.now().toEpochMilli()).append(" ");
        cef.append("deviceHostName=").append(escapeCefExtension(hostName)).append(" ");

        if (additionalFields != null) {
            additionalFields.forEach((key, value) ->
                    cef.append(key).append("=").append(escapeCefExtension(value)).append(" ")
            );
        }

        SIEM_LOGGER.info(cef.toString().trim());
    }

    private String escapeCefField(final String value) {
        if (value == null) return "";
        return value.replace("|", "\\|")
                .replace("\\", "\\\\");
    }

    private String escapeCefExtension(final String value) {
        if (value == null) return "";
        return value.replace("=", "\\=")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

}
