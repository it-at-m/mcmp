package de.muenchen.mcmp.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class JobUtilsTest {

    @Nested
    @DisplayName("Tests for isItmDepartment")
    class IsItmDepartmentTests {

        @ParameterizedTest
        @ValueSource(strings = {"ITM-123", "itm-abc", "Itm-Sales", "ITM-", "iTm-Service", "ITM-KM-CC"})
        @DisplayName("Should return true for valid ITM prefixes (case-insensitive)")
        void shouldReturnTrueForValidPrefix(String input) {
            assertTrue(JobUtils.isItmDepartment(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"IT-123", "M-123", "OTHER-ITM-", "Department", ""})
        @DisplayName("Should return false for invalid prefixes")
        void shouldReturnFalseForInvalidPrefix(String input) {
            assertFalse(JobUtils.isItmDepartment(input));
        }

        @Test
        @DisplayName("Should return false for null input")
        void shouldReturnFalseForNull() {
            assertFalse(JobUtils.isItmDepartment(null));
        }
    }

    @Nested
    @DisplayName("Tests for removeItmPrefix")
    class RemoveItmPrefixTests {

        @ParameterizedTest
        @CsvSource({
                "ITM-Finance, Finance",
                "itm-Development, Development",
                "Itm-Support, Support",
                "ITM-, ''",
                "ITM-KM-CC, KM-CC",
        })
        @DisplayName("Should remove prefix regardless of casing")
        void shouldRemovePrefix(String input, String expected) {
            assertEquals(expected, JobUtils.removeItmPrefix(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Finance", "IT-Support", "M-ITM-1", ""})
        @DisplayName("Should return original string if prefix is missing")
        void shouldReturnOriginalIfPrefixMissing(String input) {
            assertEquals(input, JobUtils.removeItmPrefix(input));
        }

        @Test
        @DisplayName("Should return null if input is null")
        void shouldReturnNullForNull() {
            assertNull(JobUtils.removeItmPrefix(null));
        }

        @Test
        @DisplayName("Should handle strings shorter than prefix correctly")
        void shouldHandleShortStrings() {
            String shortString = "IT";
            assertFalse(JobUtils.isItmDepartment(shortString));
            assertEquals(shortString, JobUtils.removeItmPrefix(shortString));
        }
    }
}