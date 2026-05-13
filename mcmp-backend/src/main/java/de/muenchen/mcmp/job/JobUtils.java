package de.muenchen.mcmp.job;

public final class JobUtils {

    private static final String ITM_DEPARTMENT_PREFIX = "ITM-";

    private JobUtils() {
        // Utility class
    }

    /**
     * Checks if the given department name starts with the "ITM-" prefix (case-insensitive).
     *
     * @param departmentName The department name to check.
     * @return true if it starts with "ITM-", false otherwise.
     */
    public static boolean isItmDepartment(String departmentName) {
        if (departmentName == null) {
            return false;
        }
        return departmentName.regionMatches(true, 0, ITM_DEPARTMENT_PREFIX, 0, ITM_DEPARTMENT_PREFIX.length());
    }

    /**
     * Removes the "ITM-" prefix from the beginning of the string (case-insensitive).
     * If the string does not start with the prefix, the original string is returned.
     *
     * @param departmentName The department name to process.
     * @return The department name without the "ITM-" prefix, or the original string.
     */
    public static String removeItmPrefix(String departmentName) {
        if (departmentName == null) {
            return null;
        }
        if (isItmDepartment(departmentName)) {
            return departmentName.substring(ITM_DEPARTMENT_PREFIX.length());
        }
        return departmentName;
    }
}