package de.muenchen.mcmp.utils;

/**
 * Utility class for logging related operations.
 */
public final class LogUtils {

    private LogUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Sanitizes a string for logging by replacing CR and LF characters to prevent log injection.
     *
     * @param input the string to sanitize
     * @return the sanitized string or an empty string if input was null
     */
    public static String sanitize(final String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[\r\n]", "_");
    }

    /**
     * Sanitizes the string representation of an object for logging.
     *
     * @param input the object whose toString() representation should be sanitized
     * @return the sanitized string or an empty string if input was null
     */
    public static String sanitize(final Object input) {
        if (input == null) {
            return "";
        }
        return sanitize(input.toString());
    }
}
