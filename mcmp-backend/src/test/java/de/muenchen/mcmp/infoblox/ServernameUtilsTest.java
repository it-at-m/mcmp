package de.muenchen.mcmp.infoblox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;


/**
 * The ServernameUtilsTest class is a test class designed to verify the functionality
 * and correctness of the ServernameUtils class. This class includes a series of unit
 * tests to ensure that methods within ServernameUtils behave as expected.
 * <p>
 * It is intended to be used within a testing framework such as JUnit to automate the
 * verification of features and identify potential issues in the ServernameUtils implementation.
 * <p>
 * This class does not contain any fields or instance variables and directly extends
 * java.lang.Object.
 */
@DisplayName("ServernameUtils Tests")
class ServernameUtilsTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor should throw UnsupportedOperationException")
        void constructorShouldThrowUnsupportedOperationException() {
            InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> {
                var constructor = ServernameUtils.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructor.newInstance();
            });

            // Check that the original cause is an UnsupportedOperationException
            assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
            assertEquals("Utility class cannot be instantiated", exception.getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("normalizeAndValidatePrefix Tests")
    class NormalizeAndValidatePrefixTests {

        @Test
        @DisplayName("Null prefix should return empty string")
        void nullPrefixShouldReturnEmptyString() throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidatePrefix(null, "lx");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Empty prefix should return empty string")
        void emptyPrefixShouldReturnEmptyString() throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidatePrefix("", "lx");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Blank prefix should return empty string")
        void blankPrefixShouldReturnEmptyString() throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidatePrefix("   ", "lx");
            assertEquals("", result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"cg-", "cl-", "cn-"})
        @DisplayName("Valid prefixes should be returned normalized")
        void validPrefixesShouldReturnNormalized(String prefix) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidatePrefix(prefix, "lx");
            assertEquals(prefix.toLowerCase(), result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"CG-", "CL-", "CN-", " cg- ", " CL- ", "  cn-  "})
        @DisplayName("Prefixes with different case and whitespace should be normalized")
        void prefixesWithDifferentCaseAndWhitespaceShouldBeNormalized(String prefix) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidatePrefix(prefix, "lx");
            assertTrue(result.matches("^(cg-|cl-|cn-)$"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid-", "xx-", "c-", "cg", "cl", "cn", "cg--", "cl--", "cn--"})
        @DisplayName("Invalid prefixes should throw IllegalArgumentException")
        void invalidPrefixesShouldThrowException(String prefix) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidatePrefix(prefix, "lx"));
            assertEquals("Ungültiger Präfix! Erlaubte Zeichen c[gln]-", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"cg-", "cl-", "cn-"})
        @DisplayName("Oracle database (db) should not allow prefixes")
        void oracleDbShouldNotAllowPrefixes(String prefix) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidatePrefix(prefix, "db"));
            assertEquals("Ungültiger Präfix! Bei Oracle Datenbanken ist kein Präfix erlaubt!", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"DB", "Db", "dB"})
        @DisplayName("Oracle database case-insensitive should not allow prefixes")
        void oracleDbCaseInsensitiveShouldNotAllowPrefixes(String serverType) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidatePrefix("cg-", serverType));
            assertEquals("Ungültiger Präfix! Bei Oracle Datenbanken ist kein Präfix erlaubt!", exception.getMessage());
        }

        @Test
        @DisplayName("Empty prefix with Oracle DB should be allowed")
        void emptyPrefixWithOracleDbShouldBeAllowed() throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidatePrefix("", "db");
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("normalizeAndValidateApplication Tests")
    class NormalizeAndValidateApplicationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("Null or empty application should throw exception")
        void nullOrEmptyApplicationShouldThrowException(String application) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication(application, false));
            assertEquals("Der Name der Applikation darf nicht leer sein!", exception.getMessage());
        }

        // Tests for applications with prefix (hasPrefix = true)
        @ParameterizedTest
        @ValueSource(strings = {"abc", "a23", "test123", "myapp567", "w234567"})
        @DisplayName("Valid applications with prefix should be accepted")
        void validApplicationsWithPrefixShouldBeAccepted(String application) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateApplication(application, true);
            assertEquals(application.toLowerCase(), result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ABC", "MyApp", " TEST ", "  WebServer  "})
        @DisplayName("Applications with prefix should be normalized")
        void applicationsWithPrefixShouldBeNormalized(String application) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateApplication(application, true);
            assertEquals(application.trim().toLowerCase(), result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "ab", "a234567890", "1abc", "ab-cd", "app_test", "toolongname123"})
        @DisplayName("Invalid applications with prefix should throw exception")
        void invalidApplicationsWithPrefixShouldThrowException(String application) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication(application, true));
            assertEquals("Ungültiger Applikationsname! Erlaubte Zeichen: [a-z][a-z0-9]{2,8}", exception.getMessage());
        }

        // Tests for applications without prefix (hasPrefix = false)
        @ParameterizedTest
        @ValueSource(strings = {"abc", "a23", "test123", "myapp567", "webserver123", "t23456789012"})
        @DisplayName("Valid applications without prefix should be accepted")
        void validApplicationsWithoutPrefixShouldBeAccepted(String application) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateApplication(application, false);
            assertEquals(application.toLowerCase(), result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "ab", "a234567890123", "1abc", "ab-cd", "app_test", "toolongnametoo"})
        @DisplayName("Invalid applications without prefix should throw exception")
        void invalidApplicationsWithoutPrefixShouldThrowException(String application) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication(application, false));
            assertEquals("Ungültiger Applikationsname! Erlaubte Zeichen: [a-z][a-z0-9]{2,11}", exception.getMessage());
        }

        @Test
        @DisplayName("Test boundary values for application with prefix")
        void testApplicationWithPrefixBoundaries() throws IllegalArgumentException {
            // Minimum: 3 characters
            assertEquals("abc", ServernameUtils.normalizeAndValidateApplication("abc", true));
            // Maximum: 9 characters
            assertEquals("abcdefghi", ServernameUtils.normalizeAndValidateApplication("abcdefghi", true));

            // Too short
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication("ab", true));
            // Too long
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication("abcdefghij", true));
        }

        @Test
        @DisplayName("Test boundary values for application without prefix")
        void testApplicationWithoutPrefixBoundaries() throws IllegalArgumentException {
            // Minimum: 3 characters
            assertEquals("abc", ServernameUtils.normalizeAndValidateApplication("abc", false));
            // Maximum: 12 characters
            assertEquals("abcdefghijkl", ServernameUtils.normalizeAndValidateApplication("abcdefghijkl", false));

            // Too short
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication("ab", false));
            // Too long
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication("abcdefghijklm", false));
        }
    }

    @Nested
    @DisplayName("normalizeAndValidateServerType Tests")
    class NormalizeAndValidateServerTypeTests {

        @ParameterizedTest
        @ValueSource(strings = {"da", "db", "dm", "dp", "ds", "dy", "lx", "wi"})
        @DisplayName("Valid server types should be accepted")
        void validServerTypesShouldBeAccepted(String serverType) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateServerType(serverType);
            assertEquals(serverType, result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"DA", "DB", "DM", "DP", "DS", "DY", "LX", "WI"})
        @DisplayName("Server types in uppercase should be normalized")
        void serverTypesInUpperCaseShouldBeNormalized(String serverType) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateServerType(serverType);
            assertEquals(serverType.toLowerCase(), result);
        }

        @ParameterizedTest
        @ValueSource(strings = {" da ", "  DB  ", "\tLX\t", "\nWI\n"})
        @DisplayName("Server types with whitespace should be normalized")
        void serverTypesWithWhitespaceShouldBeNormalized(String serverType) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateServerType(serverType);
            assertEquals(serverType.trim().toLowerCase(), result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "xx", "invalid", "d", "database", "linux", "windows"})
        @DisplayName("Invalid server types should throw exception")
        void invalidServerTypesShouldThrowException(String serverType) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateServerType(serverType));
            assertEquals("Ungültiger Servertyp! Erlaubte Zeichen d[abmpsy]|wi|lx", exception.getMessage());
        }

        @Test
        @DisplayName("Null server type should throw exception")
        void nullServerTypeShouldThrowException() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateServerType(null));
            assertEquals("Ungültiger Servertyp! Erlaubte Zeichen d[abmpsy]|wi|lx", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("validateCustomNumber Tests")
    class ValidateCustomNumberTests {

        @Test
        @DisplayName("Null custom number should return default value")
        void nullCustomNumberShouldReturnDefault() throws IllegalArgumentException {
            String result = ServernameUtils.formatCustomNumber(ServernameUtils.validateCustomNumber(null));
            assertEquals("001", result);
        }

        @ParameterizedTest
        @CsvSource({
                "1, 001",
                "10, 010",
                "99, 099",
                "100, 100",
                "500, 500",
                "999, 999"
        })
        @DisplayName("Valid custom numbers should be formatted correctly")
        void validCustomNumbersShouldBeFormattedCorrectly(Integer number, String expected) throws IllegalArgumentException {
            String result = ServernameUtils.formatCustomNumber(ServernameUtils.validateCustomNumber(number));
            assertEquals(expected, result);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10, 1000, 1001, Integer.MAX_VALUE, Integer.MIN_VALUE})
        @DisplayName("Invalid custom numbers should throw exception")
        void invalidCustomNumbersShouldThrowException(Integer number) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.validateCustomNumber(number));
            assertEquals("Ungültige laufende Nummer! Erlaubter Bereich: 1-999", exception.getMessage());
        }

        @Test
        @DisplayName("Test boundary values of custom numbers")
        void testCustomNumberBoundaries() throws IllegalArgumentException {
            // Minimum allowed value
            assertEquals("001", ServernameUtils.formatCustomNumber(ServernameUtils.validateCustomNumber(1)));
            // Maximum allowed value
            assertEquals("999", ServernameUtils.formatCustomNumber(ServernameUtils.validateCustomNumber(999)));

            // Below minimum
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.formatCustomNumber(ServernameUtils.validateCustomNumber(0)));
            // Above maximum
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.formatCustomNumber(ServernameUtils.validateCustomNumber(1000)));
        }
    }

    @Nested
    @DisplayName("normalizeAndValidateDomain Tests")
    class NormalizeAndValidateDomainTests {

        @Test
        @DisplayName("Valid domain should be returned normalized")
        void validDomainShouldReturnNormalized() throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateDomain("srv.muenchen.de");
            assertEquals("srv.muenchen.de", result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"SRV.MUENCHEN.DE", "Srv.Muenchen.De", " srv.muenchen.de ", "  SRV.MUENCHEN.DE  "})
        @DisplayName("Domain with different case should be normalized")
        void domainWithDifferentCaseShouldBeNormalized(String domain) throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateDomain(domain);
            assertEquals("srv.muenchen.de", result);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("Null or empty domain should throw exception")
        void nullOrEmptyDomainShouldThrowException(String domain) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateDomain(domain));
            assertEquals("Die Domain darf nicht leer sein!", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid.domain", "google.com", "test.de", "srv.berlin.de", "muenchen.de"})
        @DisplayName("Invalid domains should throw exception")
        void invalidDomainsShouldThrowException(String domain) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateDomain(domain));
            assertEquals("Ungültige Domain!", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Complete workflow with valid values")
        void completeWorkflowWithValidValues() throws IllegalArgumentException {
            // Test a complete sequence of validations
            String prefix = ServernameUtils.normalizeAndValidatePrefix("CG-", "lx");
            String application = ServernameUtils.normalizeAndValidateApplication("TestApp", true);
            String serverType = ServernameUtils.normalizeAndValidateServerType("LX");
            String customNumber = ServernameUtils.formatCustomNumber(ServernameUtils.validateCustomNumber(42));
            String domain = ServernameUtils.normalizeAndValidateDomain("srv.muenchen.de");

            assertEquals("cg-", prefix);
            assertEquals("testapp", application);
            assertEquals("lx", serverType);
            assertEquals("042", customNumber);
            assertEquals("srv.muenchen.de", domain);
        }

        @Test
        @DisplayName("Oracle DB workflow without prefix")
        void oracleDbWorkflowWithoutPrefix() throws IllegalArgumentException {
            String prefix = ServernameUtils.normalizeAndValidatePrefix("", "db");
            String application = ServernameUtils.normalizeAndValidateApplication("OracleApp", false);
            String serverType = ServernameUtils.normalizeAndValidateServerType("db");
            String customNumber = ServernameUtils.formatCustomNumber(ServernameUtils.validateCustomNumber(null));
            String domain = ServernameUtils.normalizeAndValidateDomain("srv.muenchen.de");

            assertEquals("", prefix);
            assertEquals("oracleapp", application);
            assertEquals("db", serverType);
            assertEquals("001", customNumber);
            assertEquals("srv.muenchen.de", domain);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Special Characters Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Application with numbers at the end")
        void applicationWithNumbersAtEnd() throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateApplication("app123", true);
            assertEquals("app123", result);
        }

        @Test
        @DisplayName("Application with mixed characters")
        void applicationWithMixedCharacters() throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidateApplication("a1b2c3", true);
            assertEquals("a1b2c3", result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"app!", "test@server", "my#app", "app$1", "test%", "app&co", "test*", "app+", "test=", "app?", "my|app"})
        @DisplayName("Application with special characters should throw exception")
        void applicationWithSpecialCharactersShouldThrowException(String application) {
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication(application, true));
        }

        @Test
        @DisplayName("Very long inputs")
        void veryLongInputs() {
            String veryLongApplication = "a".repeat(100);
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication(veryLongApplication, false));
        }

        @Test
        @DisplayName("Unicode characters in application")
        void unicodeCharactersInApplication() {
            assertThrows(IllegalArgumentException.class, () ->
                    ServernameUtils.normalizeAndValidateApplication("appüöä", true));
        }

        @Test
        @DisplayName("Different whitespace characters")
        void differentWhitespaceCharacters() throws IllegalArgumentException {
            String result = ServernameUtils.normalizeAndValidatePrefix("\t\ncg-\r ", "lx");
            assertEquals("cg-", result);
        }
    }
}