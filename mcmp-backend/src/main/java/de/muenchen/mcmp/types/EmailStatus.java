package de.muenchen.mcmp.types;

/**
 * Represents the possible statuses of an email in a system.
 * EmailStatus is an enumeration that provides the following states:
 * - NEW: Indicates that the email is newly created and has not been processed or sent yet.
 * - SENT: Indicates that the email has been successfully sent.
 * - SKIPPED: Indicates that sending the email was intentionally skipped.
 * - FAILED: Indicates that sending the email was attempted but failed.
 */
public enum EmailStatus {
    NEW,
    SENT,
    SKIPPED,
    FAILED
}