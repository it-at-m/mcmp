
package de.muenchen.mcmp.temporaryPrivileges;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for handling PrivilegeType enum conversion between entity attributes and database columns.
 *
 * This converter automatically applies to all PrivilegeType fields in JPA entities,
 * converting enum values to uppercase strings when storing to database and
 * converting database strings back to enum values when loading entities.
 */
@Converter(autoApply = true)
public class TemporaryPrivilegeConverter implements AttributeConverter<PrivilegeType, String> {

    /**
     * Converts the PrivilegeType enum attribute to its database column representation.
     *
     * This method is called when persisting an entity to the database.
     * It converts the enum value to an uppercase string representation.
     *
     * @param attribute The PrivilegeType enum value to convert, can be null
     * @return The uppercase string representation of the enum, or null if input is null
     */
    @Override
    public String convertToDatabaseColumn(final PrivilegeType attribute) {
        return attribute == null ? null : attribute.name().toUpperCase();
    }

    /**
     * Converts the database column data back to a PrivilegeType enum attribute.
     *
     * This method is called when loading an entity from the database.
     * It converts the stored string value back to the corresponding enum value.
     *
     * @param dbData The string value from the database column, can be null
     * @return The corresponding PrivilegeType enum value, or null if input is null
     * @throws IllegalArgumentException if the database string doesn't match any enum constant
     */
    @Override
    public PrivilegeType convertToEntityAttribute(final String dbData) {
        return dbData == null ? null : PrivilegeType.valueOf(dbData.toUpperCase());
    }
}