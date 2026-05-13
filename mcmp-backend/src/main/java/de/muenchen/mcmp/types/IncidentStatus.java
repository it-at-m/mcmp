package de.muenchen.mcmp.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IncidentStatus {
    OPEN,
    RESOLVED,
    FAILED;

    @JsonValue
    public String getValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static IncidentStatus fromValue(String value) {
        for (IncidentStatus type : IncidentStatus.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown IncidentStatus: " + value);
    }
}
