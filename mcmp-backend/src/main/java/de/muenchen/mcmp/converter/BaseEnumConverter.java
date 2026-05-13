package de.muenchen.mcmp.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Generic base implementation of a JPA {@link AttributeConverter} for enums that are stored as {@link String}s.
 * <p>
 * This converter provides a consistent, case-insensitive mapping between Java enum constants and the database:
 * <ul>
 *   <li><b>Entity → DB:</b> {@code enum.name()} is persisted in <b>lowercase</b>.</li>
 *   <li><b>DB → Entity:</b> the stored value is converted to <b>uppercase</b> and resolved via {@link Enum#valueOf(Class, String)}.</li>
 * </ul>
 * <p>
 * {@code null} values are handled gracefully in both directions.
 * <p>
 * Usage pattern: create a concrete converter per enum and pass the enum class to the constructor:
 * <pre>{@code
 * @Converter(autoApply = true)
 * public class DbTypeConverter extends BaseEnumConverter<DbType> {
 *   public DbTypeConverter() { super(DbType.class); }
 * }
 * }</pre>
 * <p>
 * Note: {@link Enum#valueOf(Class, String)} throws {@link IllegalArgumentException} if the database contains an
 * unknown value (after uppercasing). Ensure the DB content matches the enum constants (ignoring case).
 *
 * @param <E> the enum type handled by this converter
 */
@Converter
public abstract class BaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumClass;

    /**
     * Creates a new converter for the given enum type.
     *
     * @param enumClass the enum class used to resolve values from the database
     */
    protected BaseEnumConverter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    /**
     * Converts an enum value to its database representation.
     *
     * @param attribute the enum value from the entity (may be {@code null})
     * @return the lowercase enum name to store in the database, or {@code null}
     */
    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    /**
     * Converts a database value back to the corresponding enum constant.
     *
     * @param dbData the value read from the database (may be {@code null})
     * @return the resolved enum constant, or {@code null}
     * @throws IllegalArgumentException if {@code dbData} does not match any enum constant (case-insensitive)
     */
    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumClass, dbData.toUpperCase());
    }
}