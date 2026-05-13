package de.muenchen.mcmp.server;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility class providing server-related helper methods.
 *
 * <p>This class contains static utility methods for server operations, particularly
 * for handling VMware server identifiers and their conversion to standardized formats.</p>
 *
 * <p>The class is designed as a final utility class with a private constructor to
 * prevent instantiation, following the utility class design pattern.</p>
 */
@Slf4j
public final class ServerUtils {

    /**
     * VMware serial number prefix that identifies VMware-generated serial numbers.
     */
    private static final String VMWARE_PREFIX = "VMware-";

    /**
     * Expected length of a cleaned UUID string (without hyphens and spaces).
     */
    private static final int UUID_LENGTH_WITHOUT_HYPHENS = 32;

    /**
     * Pattern for matching whitespace and hyphens that need to be removed during UUID conversion.
     */
    private static final Pattern CLEANUP_PATTERN = Pattern.compile("[\\s-]");

    /**
     * Pattern for validating hexadecimal characters.
     */
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]+$");

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException if called via reflection
     */
    private ServerUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts a serial number to a standard UUID string if it matches the VMware serial format.
     *
     * <p>If the input does not represent a VMware serial, or conversion/validation fails, the original
     * input is returned unchanged.</p>
     *
     * <p><b>Null handling:</b> This method does not throw on {@code null}; it will return {@code null}
     * if the provided {@code serial} is {@code null}.</p>
     *
     * @param serial the serial number to convert; may be a VMware serial or any other identifier (may be {@code null})
     * @return a standard UUID string if conversion succeeds; otherwise the original {@code serial}
     */
    public static String convertSerialToUUID(final String serial) {
        if (isValidVmwareSerial(serial)) {
            var uuid = convertVmwareSerialToUuid(serial);
            if (uuid != null) {
                return uuid;
            }
        }
        return serial;
    }


    /**
     * Converts a VMware serial number to a standardized UUID format.
     *
     * <p>VMware serial numbers typically follow the format "VMware-XX XX XX XX XX XX XX XX-XX XX XX XX XX XX XX XX"
     * where XX represents hexadecimal digits. This method extracts the hexadecimal portion,
     * removes spaces and hyphens, and formats it as a standard UUID string.</p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>
     * Input:  "VMware-56 4d 2c 5f 8a 92 1a 2f-87 3e 9c 1d 4b 5a 6e 7f"
     * Output: "564d2c5f-8a92-1a2f-873e-9c1d4b5a6e7f"
     * </pre>
     *
     * <p><strong>Validation:</strong></p>
     * <ul>
     *   <li>Input must not be null</li>
     *   <li>Input must start with "VMware-" prefix</li>
     *   <li>After cleanup, the hexadecimal string must be exactly 32 characters long</li>
     *   <li>The resulting UUID string must be valid according to UUID.fromString()</li>
     * </ul>
     *
     * @param vmwareSerial the VMware serial number to convert, must not be null and must start with "VMware-"
     * @return the formatted UUID string in standard UUID format (8-4-4-4-12), or null if conversion fails
     * @throws IllegalArgumentException if vmwareSerial is null
     * @see UUID#fromString(String)
     * @see #isValidVmwareSerial(String)
     */
    static String convertVmwareSerialToUuid(final String vmwareSerial) {
        if (vmwareSerial == null) {
            throw new IllegalArgumentException("VMware serial number cannot be null");
        }

        if (!isValidVmwareSerial(vmwareSerial)) {
            log.debug("Invalid VMware serial format: {}", vmwareSerial);
            return null;
        }

        try {
            final String withoutPrefix = vmwareSerial.substring(VMWARE_PREFIX.length());
            final String cleaned = CLEANUP_PATTERN.matcher(withoutPrefix).replaceAll("");

            if (cleaned.length() != UUID_LENGTH_WITHOUT_HYPHENS) {
                log.debug("Invalid UUID length after cleanup. Expected: {}, Actual: {}, Serial: {}",
                        UUID_LENGTH_WITHOUT_HYPHENS, cleaned.length(), vmwareSerial);
                return null;
            }

            // Validate that the cleaned string contains only hexadecimal characters
            if (!HEX_PATTERN.matcher(cleaned).matches()) {
                log.debug("Invalid hexadecimal characters found in cleaned serial: {}", cleaned);
                return null;
            }

            final String formatted = formatAsUuid(cleaned);

            // Validate that the formatted string is a valid UUID
            UUID.fromString(formatted);

            log.debug("Successfully converted VMware serial to UUID: {} -> {}", vmwareSerial, formatted);
            return formatted;

        } catch (final StringIndexOutOfBoundsException e) {
            log.warn("String index out of bounds while converting VMware serial: {}", vmwareSerial, e);
            return null;
        } catch (final IllegalArgumentException e) {
            log.warn("Invalid UUID format generated from VMware serial: {}", vmwareSerial, e);
            return null;
        } catch (final Exception e) {
            log.error("Unexpected error converting VMware serial to UUID: {}", vmwareSerial, e);
            return null;
        }
    }

    /**
     * Checks if the given string represents a valid VMware serial number format.
     *
     * <p>A valid VMware serial number must:</p>
     * <ul>
     *   <li>Not be null</li>
     *   <li>Start with the "VMware-" prefix</li>
     *   <li>Have sufficient length to contain UUID data after the prefix</li>
     * </ul>
     *
     * @param vmwareSerial the serial number to validate
     * @return true if the serial number has a valid VMware format, false otherwise
     */
    private static boolean isValidVmwareSerial(final String vmwareSerial) {
        return vmwareSerial != null
               && vmwareSerial.startsWith(VMWARE_PREFIX)
               && vmwareSerial.length() > VMWARE_PREFIX.length();
    }

    /**
     * Formats a 32-character hexadecimal string into standard UUID format.
     *
     * <p>Converts a string like "564d2c5f8a921a2f873e9c1d4b5a6e7f"
     * into UUID format "564d2c5f-8a92-1a2f-873e-9c1d4b5a6e7f".</p>
     *
     * @param cleanedHex the 32-character hexadecimal string
     * @return the formatted UUID string
     * @throws StringIndexOutOfBoundsException if the input string is too short
     */
    private static String formatAsUuid(final String cleanedHex) {
        return String.format("%s-%s-%s-%s-%s",
                cleanedHex.substring(0, 8),
                cleanedHex.substring(8, 12),
                cleanedHex.substring(12, 16),
                cleanedHex.substring(16, 20),
                cleanedHex.substring(20, 32));
    }
}