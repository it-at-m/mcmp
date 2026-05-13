package de.muenchen.mcmp.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link ServerUtils} utility class.
 *
 * <p>This test class provides comprehensive test coverage for all public methods
 * in the ServerUtils class, focusing particularly on the VMware serial number
 * to UUID conversion functionality.</p>
 *
 * <p>The tests are organized into nested test classes for better structure and
 * readability, covering various scenarios including valid inputs, invalid inputs,
 * edge cases, and error conditions.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>Constructor tests - Verify utility class instantiation behavior</li>
 *   <li>Valid conversion tests - Test successful VMware serial to UUID conversions</li>
 *   <li>Invalid input tests - Test handling of malformed or invalid inputs</li>
 *   <li>Null and empty input tests - Test edge cases with null/empty values</li>
 *   <li>Error handling tests - Verify proper exception handling</li>
 * </ul>
 *
 * @see ServerUtils
 */
@DisplayName("ServerUtils Unit Tests")
class ServerUtilsTest {

    /**
     * Tests for the utility class constructor behavior.
     *
     * <p>Verifies that the utility class follows proper design patterns
     * by preventing instantiation through reflection.</p>
     */
    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        /**
         * Tests that attempting to instantiate the utility class via reflection
         * throws an UnsupportedOperationException.
         *
         * <p>This test ensures that the utility class design pattern is properly
         * implemented with a private constructor that throws an exception when called.</p>
         */
        @Test
        @DisplayName("Should throw UnsupportedOperationException when instantiated via reflection")
        void constructor_whenCalledViaReflection_shouldThrowUnsupportedOperationException() {
            final var exception = assertThrows(InvocationTargetException.class, () -> {
                final var constructor = ServerUtils.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructor.newInstance();
            }, "Constructor should throw InvocationTargetException when called via reflection");

            // Verify that the cause is UnsupportedOperationException
            assertInstanceOf(UnsupportedOperationException.class, exception.getCause(),
                    "The cause of InvocationTargetException should be UnsupportedOperationException");
            assertEquals("Utility class cannot be instantiated", exception.getCause().getMessage(),
                    "The exception message should match the expected message");
        }

    }

    /**
     * Tests for valid VMware serial number to UUID conversions.
     *
     * <p>This nested class contains tests that verify the correct conversion
     * of properly formatted VMware serial numbers to standard UUID format.</p>
     */
    @Nested
    @DisplayName("Valid VMware Serial Conversion Tests")
    class ValidConversionTests {

        /**
         * Tests conversion of a standard VMware serial number format.
         *
         * <p>Verifies that a properly formatted VMware serial number with spaces
         * and hyphens is correctly converted to standard UUID format.</p>
         */
        @Test
        @DisplayName("Should convert standard VMware serial with spaces to UUID")
        void convertVmwareSerialToUuid_withValidSerialWithSpaces_shouldReturnCorrectUuid() {
            // Given
            final String serialNumber = "VMware-56 4d 2d 4a a1 95 7c 9f-8b e0 6c 8a aa 7e 4e 72";
            final String expectedUuid = "564d2d4a-a195-7c9f-8be0-6c8aaa7e4e72";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertEquals(expectedUuid, result,
                    "The UUID should match the expected format when converting a valid VMware serial number");
        }

        /**
         * Tests conversion of a VMware serial number without spaces.
         *
         * <p>Verifies that VMware serial numbers without spaces are also
         * handled correctly during conversion.</p>
         */
        @Test
        @DisplayName("Should convert VMware serial without spaces to UUID")
        void convertVmwareSerialToUuid_withValidSerialWithoutSpaces_shouldReturnCorrectUuid() {
            // Given
            final String serialNumber = "VMware-564d2d4aa1957c9f-8be06c8aaa7e4e72";
            final String expectedUuid = "564d2d4a-a195-7c9f-8be0-6c8aaa7e4e72";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertEquals(expectedUuid, result,
                    "The UUID should match the expected format when converting a VMware serial without spaces");
        }

        /**
         * Tests conversion of a mixed format VMware serial number.
         *
         * <p>Verifies handling of VMware serial numbers with mixed spacing
         * and hyphen patterns.</p>
         */
        @Test
        @DisplayName("Should convert mixed format VMware serial to UUID")
        void convertVmwareSerialToUuid_withMixedFormat_shouldReturnCorrectUuid() {
            // Given
            final String serialNumber = "VMware-564d2d4a-a1957c9f8be06c8a-aa7e4e72";
            final String expectedUuid = "564d2d4a-a195-7c9f-8be0-6c8aaa7e4e72";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertEquals(expectedUuid, result,
                    "The UUID should match the expected format when converting a mixed format VMware serial");
        }

        /**
         * Tests conversion with uppercase hexadecimal characters.
         *
         * <p>Verifies that VMware serial numbers with uppercase hexadecimal
         * characters are properly handled.</p>
         */
        @Test
        @DisplayName("Should convert VMware serial with uppercase hex to UUID")
        void convertVmwareSerialToUuid_withUppercaseHex_shouldReturnCorrectUuid() {
            // Given
            final String serialNumber = "VMware-564D2D4A A195 7C9F-8BE0 6C8A AA7E 4E72";
            final String expectedUuid = "564D2D4A-A195-7C9F-8BE0-6C8AAA7E4E72";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertEquals(expectedUuid, result,
                    "The UUID should preserve case when converting VMware serial with uppercase characters");
        }
    }

    /**
     * Tests for invalid input handling.
     *
     * <p>This nested class contains tests that verify proper handling of
     * various invalid input scenarios.</p>
     */
    @Nested
    @DisplayName("Invalid Input Tests")
    class InvalidInputTests {

        /**
         * Tests handling of completely invalid serial number format.
         *
         * <p>Verifies that serial numbers not starting with "VMware-" prefix
         * return null without throwing exceptions.</p>
         */
        @Test
        @DisplayName("Should return null for invalid serial format")
        void convertVmwareSerialToUuid_withInvalidSerial_shouldReturnNull() {
            // Given
            final String serialNumber = "InvalidSerial";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "The result should be null for a serial number that doesn't start with VMware- prefix");
        }

        /**
         * Tests handling of malformed VMware serial numbers.
         *
         * <p>Verifies that VMware serial numbers with correct prefix but
         * invalid content return null.</p>
         */
        @Test
        @DisplayName("Should return null for malformed VMware serial")
        void convertVmwareSerialToUuid_withMalformedSerial_shouldReturnNull() {
            // Given
            final String serialNumber = "VMware-Invalid-123";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "The result should be null for a malformed VMware serial number");
        }

        /**
         * Tests handling of VMware serial with insufficient data.
         *
         * <p>Verifies that VMware serial numbers with correct prefix but
         * insufficient hexadecimal data return null.</p>
         */
        @Test
        @DisplayName("Should return null for VMware serial with insufficient data")
        void convertVmwareSerialToUuid_withInsufficientData_shouldReturnNull() {
            // Given
            final String serialNumber = "VMware-123";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "The result should be null for a VMware serial with insufficient hexadecimal data");
        }

        /**
         * Tests handling of VMware serial with too much data.
         *
         * <p>Verifies that VMware serial numbers with more than 32 hexadecimal
         * characters return null.</p>
         */
        @Test
        @DisplayName("Should return null for VMware serial with too much data")
        void convertVmwareSerialToUuid_withTooMuchData_shouldReturnNull() {
            // Given
            final String serialNumber = "VMware-564d2d4aa1957c9f8be06c8aaa7e4e72123456789";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "The result should be null for a VMware serial with more than 32 hexadecimal characters");
        }

        /**
         * Tests handling of VMware serial with invalid hexadecimal characters.
         *
         * <p>Verifies that VMware serial numbers containing non-hexadecimal
         * characters return null.</p>
         */
        @Test
        @DisplayName("Should return null for VMware serial with invalid hex characters")
        void convertVmwareSerialToUuid_withInvalidHexCharacters_shouldReturnNull() {
            // Given
            final String serialNumber = "VMware-564g2d4a a195 7c9f-8be0 6c8a aa7e 4e72";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "The result should be null for a VMware serial containing invalid hexadecimal characters");
        }
    }

    /**
     * Tests for null and empty input handling.
     *
     * <p>This nested class contains tests that verify proper handling of
     * null and empty input values.</p>
     */
    @Nested
    @DisplayName("Null and Empty Input Tests")
    class NullAndEmptyInputTests {

        /**
         * Tests handling of null input.
         *
         * <p>Verifies that passing null to the conversion method throws
         * an IllegalArgumentException with an appropriate message.</p>
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException for null input")
        void convertVmwareSerialToUuid_withNullInput_shouldThrowIllegalArgumentException() {
            // Given
            final String serialNumber = null;

            // When & Then
            final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> ServerUtils.convertVmwareSerialToUuid(serialNumber),
                    "Should throw IllegalArgumentException when input is null");

            assertEquals("VMware serial number cannot be null", exception.getMessage(),
                    "Exception message should indicate that null input is not allowed");
        }

        /**
         * Tests handling of empty string input.
         *
         * <p>Verifies that empty strings are properly handled and return null.</p>
         */
        @Test
        @DisplayName("Should return null for empty string input")
        void convertVmwareSerialToUuid_withEmptyString_shouldReturnNull() {
            // Given
            final String serialNumber = "";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "The result should be null for an empty string input");
        }

        /**
         * Tests handling of whitespace-only input.
         *
         * <p>Verifies that strings containing only whitespace are properly handled.</p>
         */
        @Test
        @DisplayName("Should return null for whitespace-only input")
        void convertVmwareSerialToUuid_withWhitespaceOnly_shouldReturnNull() {
            // Given
            final String serialNumber = "   ";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "The result should be null for whitespace-only input");
        }

        /**
         * Tests handling of VMware prefix only.
         *
         * <p>Verifies that input containing only the VMware prefix without
         * any additional data returns null.</p>
         */
        @Test
        @DisplayName("Should return null for VMware prefix only")
        void convertVmwareSerialToUuid_withVmwarePrefixOnly_shouldReturnNull() {
            // Given
            final String serialNumber = "VMware-";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "The result should be null for input containing only the VMware prefix");
        }
    }

    /**
     * Tests for edge cases and boundary conditions.
     *
     * <p>This nested class contains tests that verify handling of various
     * edge cases and boundary conditions.</p>
     */
    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        /**
         * Tests conversion with exactly 32 hexadecimal characters.
         *
         * <p>Verifies that the boundary condition of exactly 32 characters
         * is handled correctly.</p>
         */
        @Test
        @DisplayName("Should convert VMware serial with exactly 32 hex characters")
        void convertVmwareSerialToUuid_withExactly32HexChars_shouldReturnCorrectUuid() {
            // Given
            final String serialNumber = "VMware-12345678901234567890123456789012";
            final String expectedUuid = "12345678-9012-3456-7890-123456789012";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertEquals(expectedUuid, result,
                    "Should successfully convert VMware serial with exactly 32 hexadecimal characters");
        }

        /**
         * Tests conversion with case-sensitive prefix.
         *
         * <p>Verifies that the VMware prefix is case-sensitive.</p>
         */
        @Test
        @DisplayName("Should return null for case-insensitive VMware prefix")
        void convertVmwareSerialToUuid_withWrongCasePrefix_shouldReturnNull() {
            // Given
            final String serialNumber = "vmware-564d2d4aa1957c9f8be06c8aaa7e4e72";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertNull(result,
                    "Should return null when VMware prefix has incorrect case");
        }

        /**
         * Tests conversion with various whitespace patterns.
         *
         * <p>Verifies that different whitespace patterns (tabs, multiple spaces)
         * are properly handled.</p>
         */
        @Test
        @DisplayName("Should handle various whitespace patterns")
        void convertVmwareSerialToUuid_withVariousWhitespace_shouldReturnCorrectUuid() {
            // Given
            final String serialNumber = "VMware-564d\t2d4a  a195\t7c9f-8be0  6c8a\taa7e\t4e72";
            final String expectedUuid = "564d2d4a-a195-7c9f-8be0-6c8aaa7e4e72";

            // When
            final String result = ServerUtils.convertVmwareSerialToUuid(serialNumber);

            // Then
            assertEquals(expectedUuid, result,
                    "Should properly handle various whitespace characters (spaces, tabs)");
        }
    }
}