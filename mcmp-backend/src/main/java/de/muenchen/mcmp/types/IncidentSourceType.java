package de.muenchen.mcmp.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IncidentSourceType {
    AWX,
    CHANGE,
    QUICKDISCOVERY,
    TAGGING;

    @JsonValue
    public String getValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static IncidentSourceType fromValue(String value) {
        for (IncidentSourceType type : IncidentSourceType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown IncidentSourceType: " + value);
    }
}
