package de.muenchen.mcmp.infoblox;

import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for normalizing and validating server name components.
 *
 * <p>This class provides static helper methods for processing and validating:
 * <ul>
 *   <li>Cluster prefixes (cg-, cl-, cn-)</li>
 *   <li>Application names</li>
 *   <li>Server types (databases and operating systems)</li>
 *   <li>Sequential numbers</li>
 *   <li>Domains</li>
 * </ul>
 *
 * <p>The class follows the utility class design pattern with a private constructor
 * to prevent instantiation.</p>
 */
@Slf4j
public final class ServernameUtils {

    private static final Set<String> VALID_PREFIXES = Set.of("cg-", "cl-", "cn-");
    private static final Pattern VALID_APPLICATION_WITH_PREFIX_PATTERN = Pattern.compile("^[a-z][a-z0-9]{2,8}$");
    private static final Pattern VALID_APPLICATION_WITHOUT_PREFIX_PATTERN = Pattern.compile("^[a-z][a-z0-9]{2,11}$");
    private static final Set<String> VALID_SERVER_TYPES = Set.of("da", "db", "dm", "dp", "ds", "dy", "lx", "wi");
    private static final DecimalFormat CUSTOM_NUMBER_FORMAT = new DecimalFormat("000");
    private static final Set<String> VALID_DOMAINS = Set.of("srv.muenchen.de", "testlhm.muenchen.de");
    private static final int DEFAULT_CUSTOM_NUMBER = 1;

    // Error messages
    private static final String INVALID_PREFIX_MESSAGE = "Ungültiger Präfix! Erlaubte Zeichen c[gln]-";
    private static final String INVALID_PREFIX_BY_ORACLEDB_MESSAGE = "Ungültiger Präfix! Bei Oracle Datenbanken ist kein Präfix erlaubt!";
    private static final String INVALID_APPLICATION_EMPTY_MESSAGE = "Der Name der Applikation darf nicht leer sein!";
    private static final String INVALID_APPLICATION_WITH_PREFIX_MESSAGE = "Ungültiger Applikationsname! Erlaubte Zeichen: [a-z][a-z0-9]{2,8}";
    private static final String INVALID_APPLICATION_WITHOUT_PREFIX_MESSAGE = "Ungültiger Applikationsname! Erlaubte Zeichen: [a-z][a-z0-9]{2,11}";
    private static final String INVALID_SERVER_TYPE_MESSAGE = "Ungültiger Servertyp! Erlaubte Zeichen d[abmpsy]|wi|lx";
    private static final String INVALID_CUSTOM_NUMBER_MESSAGE = "Ungültige laufende Nummer! Erlaubter Bereich: 1-999";
    private static final String INVALID_DOMAIN_MESSAGE = "Ungültige Domain!";
    private static final String INVALID_DOMAIN_EMPTY_MESSAGE = "Die Domain darf nicht leer sein!";

    private ServernameUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Normalizes and validates a given cluster notification prefix.
     * The method trims whitespace and converts to lowercase for normalization.
     *
     * <p>Valid cluster notification prefixes are:</p>
     * <ul>
     *   <li>null or empty string ("") - returns ""</li>
     *   <li>"cg-" = Cluster group / Availability group for AoAG SQL Server Cluster</li>
     *   <li>"cl-" = Virtual cluster addresses</li>
     *   <li>"cn-" = Cluster nodes</li>
     * </ul>
     *
     * @param prefix     the cluster notification prefix to normalize and validate
     * @param serverType the server type for additional validation rules
     * @return the normalized valid prefix (empty string for null/empty input)
     * @throws IllegalArgumentException if the prefix is invalid after normalization
     */
    public static String normalizeAndValidatePrefix(final String prefix, final String serverType) throws IllegalArgumentException {
        log.debug("Normalizing and validating prefix: '{}' for serverType: '{}'", prefix, serverType);
        final String normalized = normalize(prefix);
        validatePrefix(normalized, serverType);
        log.debug("Successfully validated prefix: '{}' -> '{}'", prefix, normalized);
        return normalized;
    }

    /**
     * Normalizes the input by trimming whitespace and converting to lowercase.
     *
     * @param input the input string to normalize
     * @return the normalized string, or empty string if input is null or blank
     */
    private static String normalize(final String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input.trim().toLowerCase();
    }

    /**
     * Validates the normalized prefix according to business rules.
     *
     * @param normalizedPrefix the normalized prefix to validate
     * @param serverType       the server type for additional validation
     * @throws IllegalArgumentException if validation fails
     */
    private static void validatePrefix(final String normalizedPrefix, final String serverType) throws IllegalArgumentException {
        // Empty prefixes are allowed
        if (normalizedPrefix.isEmpty()) {
            return;
        }

        if (!VALID_PREFIXES.contains(normalizedPrefix)) {
            log.debug("Invalid prefix '{}' not in valid prefixes: {}", normalizedPrefix, VALID_PREFIXES);
            throw new IllegalArgumentException(INVALID_PREFIX_MESSAGE);
        }

        // Oracle databases cannot have prefixes
        if ("db".equalsIgnoreCase(serverType)) {
            log.debug("Oracle database detected with prefix '{}' - not allowed", normalizedPrefix);
            throw new IllegalArgumentException(INVALID_PREFIX_BY_ORACLEDB_MESSAGE);
        }
    }

    /**
     * Normalizes and validates the provided application identifier.
     * This method trims the input string, converts it to lowercase for normalization,
     * and validates it using predefined patterns. The pattern used for validation
     * depends on whether the `hasPrefix` parameter is true or false.
     *
     * @param application the application identifier to be normalized and validated.
     *                    It must not be null or blank.
     * @param hasPrefix   indicates whether the application identifier has a prefix.
     *                    If true, validation includes checking for a valid prefix;
     *                    otherwise, it uses a simpler pattern without prefix validation.
     * @return the normalized application identifier if it passes validation
     * @throws IllegalArgumentException if the application identifier is null, blank,
     *                               or does not follow the expected format
     */
    public static String normalizeAndValidateApplication(final String application, final boolean hasPrefix) throws IllegalArgumentException {
        if (application == null || application.isBlank()) {
            throw new IllegalArgumentException(INVALID_APPLICATION_EMPTY_MESSAGE);
        }

        final String normalizedApplication = application.trim().toLowerCase();
        final Pattern pattern = hasPrefix ? VALID_APPLICATION_WITH_PREFIX_PATTERN : VALID_APPLICATION_WITHOUT_PREFIX_PATTERN;
        final String errorMessage = hasPrefix ? INVALID_APPLICATION_WITH_PREFIX_MESSAGE : INVALID_APPLICATION_WITHOUT_PREFIX_MESSAGE;

        if (!pattern.matcher(normalizedApplication).matches()) {
            throw new IllegalArgumentException(errorMessage);
        }

        return normalizedApplication;
    }

    /**
     * Normalizes and validates the provided server type.
     * The method ensures the input server type is properly formatted by trimming whitespace
     * and converting it to lowercase. Additionally, it checks the normalized server type
     * against a predefined list of valid server types.
     *
     * <p>Valid server types are:</p>
     * <ul>
     *   <li>"da" = MariaDB</li>
     *   <li>"db" = OracleDB</li>
     *   <li>"dm" = MongoDB</li>
     *   <li>"dp" = PostgreSQL</li>
     *   <li>"ds" = MSSQL</li>
     *   <li>"dy" = MySQL</li>
     *   <li>"lx" = Linux</li>
     *   <li>"wi" = Windows</li>
     * </ul>
     *
     * @param serverType the server type to be normalized and validated
     * @return the normalized server type if it passes validation
     * @throws IllegalArgumentException if the input server type is invalid after normalization
     */
    public static String normalizeAndValidateServerType(final String serverType) throws IllegalArgumentException {
        final String normalized = normalize(serverType);
        if (!VALID_SERVER_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(INVALID_SERVER_TYPE_MESSAGE);
        }
        return normalized;
    }

    /**
     * Validates the provided custom number.
     * If the number is null, it returns a default value of "001".
     * If the number is outside the range of 1 to 999 (inclusive), an IllegalArgumentException is thrown.
     *
     * @param number the custom number to validate. It can be null, and if so, a default value is returned.
     * @return "001" if the number is null; otherwise, the formatted number with leading zeros
     * @throws IllegalArgumentException if the number is less than 1 or greater than 999
     */
    public static int validateCustomNumber(final Integer number) throws IllegalArgumentException {
        if (number == null) {
            return DEFAULT_CUSTOM_NUMBER;
        }
        if (number < 1 || number > 999) {
            throw new IllegalArgumentException(INVALID_CUSTOM_NUMBER_MESSAGE);
        }
        return number;
    }

    public static String formatCustomNumber(final int number) {
        return CUSTOM_NUMBER_FORMAT.format(number);
    }


    /**
     * Normalizes and validates a given domain.
     * The method trims whitespace and converts to lowercase for normalization.
     *
     * <p>Valid domains are:</p>
     * <ul>
     *   <li>"srv.muenchen.de" - Standard domain for Munich</li>
     * </ul>
     *
     * @param domain the domain to normalize and validate
     * @return the normalized, valid domain
     * @throws IllegalArgumentException if the domain is null, blank, or invalid after normalization
     */
    public static String normalizeAndValidateDomain(final String domain) throws IllegalArgumentException {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException(INVALID_DOMAIN_EMPTY_MESSAGE);
        }

        final String normalized = normalize(domain);
        if (!VALID_DOMAINS.contains(normalized)) {
            throw new IllegalArgumentException(INVALID_DOMAIN_MESSAGE);
        }
        return normalized;
    }

}
