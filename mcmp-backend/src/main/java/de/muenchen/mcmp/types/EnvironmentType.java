package de.muenchen.mcmp.types;

import java.util.Arrays;

public enum EnvironmentType {
    C,
    D,
    K,
    P,
    S,
    T;


    /**
     * Converts a string representation of an environment type to its corresponding
     * {@code EnvironmentType} enum value. The comparison is case-insensitive and
     * trims any surrounding whitespace. If the input is null, empty, or does not
     * match any {@code EnvironmentType}, it returns {@code null}.
     *
     * @param environment the string representation of the environment type
     * @return the corresponding {@code EnvironmentType} if a match is found, or
     *         {@code null} if the input is null, empty, or no match exists
     */
    public static EnvironmentType fromString(String environment) {
        if (environment == null || environment.trim().isEmpty()) {
            return null;
        }

        final String normalized = environment.trim().toUpperCase();

        if ("TL".equals(normalized)) {
            return T;
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
