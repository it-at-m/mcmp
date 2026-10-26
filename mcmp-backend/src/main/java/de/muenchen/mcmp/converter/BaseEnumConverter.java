package de.muenchen.mcmp.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;
import java.util.Objects;

/**
 * Generic base implementation of a JPA {@link AttributeConverter} for enums that are stored as {@link String}s.
 * <p>
 * The converter maps between Java enum constants and the database in a configurable way:
 * <ul>
 *   <li><b>Entity → DB:</b> {@code enum.name()} is persisted either in <b>lowercase</b> ({@link Case#LOWER},
 *       the default) or verbatim, i.e. in the enum's own constant style such as {@code SERVER} ({@link Case#UPPER}).</li>
 *   <li><b>DB → Entity:</b> the stored value is always converted to <b>uppercase</b> and resolved via
 *       {@link Enum#valueOf(Class, String)}, independently of the configured storage case.</li>
 * </ul>
 * <p>
 * The read direction is deliberately case-insensitive: a column can therefore be migrated from lowercase to
 * uppercase labels (for example to align a PostgreSQL enum type with the Java constants) without breaking rows
 * that still hold the legacy lowercase value.
 * <p>
 * {@code null} values are handled gracefully in both directions.
 * <p>
 * Usage pattern: create a concrete converter per enum and pass the enum class to the constructor. Omitting the
 * {@link Case} argument keeps the historical lowercase behavior:
 * <pre>{@code
 * @Converter(autoApply = true)
 * public class DbTypeConverter extends BaseEnumConverter<DbType> {
 *   public DbTypeConverter() { super(DbType.class); }                            // persists 'successful'
 * }
 *
 * @Converter(autoApply = true)
 * public class TaggingActionConverter extends BaseEnumConverter<TaggingAction> {
 *   public TaggingActionConverter() { super(TaggingAction.class, Case.UPPER); }  // persists 'SERVER'
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

    /**
     * Storage case applied when writing enum constants to the database.
     * <p>
     * It only affects {@link #convertToDatabaseColumn}; reading via {@link #convertToEntityAttribute} is always
     * case-insensitive.
     */
    public enum Case {
        /**
         * Enum constants are persisted in lower case, e.g. {@code TaggingStatus.SUCCESSFUL} becomes
         * {@code successful}. This is the historical default and matches PostgreSQL enum types whose labels
         * are lower case.
         */
        LOWER,

        /**
         * Enum constants are persisted in upper case, i.e. unchanged from {@link Enum#name()}, e.g.
         * {@code SERVER} or {@code KUBERNETES_NAMESPACE}. Use this when the database enum labels are declared
         * in the same style as the Java constants.
         */
        UPPER;

        /**
         * Applies this case to the given enum constant name.
         *
         * @param name the value of {@link Enum#name()}, never {@code null}
         * @return the name converted to this case
         */
        private String apply(final String name) {
            return this == LOWER ? name.toLowerCase(Locale.ROOT) : name.toUpperCase(Locale.ROOT);
        }
    }

    private final Class<E> enumClass;
    private final Case nameCase;

    /**
     * Creates a new converter for the given enum type, persisting constants in lower case.
     *
     * @param enumClass the enum class used to resolve values from the database
     */
    protected BaseEnumConverter(Class<E> enumClass) {
        this(enumClass, Case.LOWER);
    }

    /**
     * Creates a new converter for the given enum type with an explicit storage case.
     *
     * @param enumClass the enum class used to resolve values from the database
     * @param nameCase  the case in which enum constants are persisted
     */
    protected BaseEnumConverter(Class<E> enumClass, Case nameCase) {
        this.enumClass = enumClass;
        this.nameCase = Objects.requireNonNull(nameCase, "nameCase");
    }

    /**
     * Converts an enum value to its database representation.
     *
     * @param attribute the enum value from the entity (may be {@code null})
     * @return the enum name in the configured {@link Case} to store in the database, or {@code null}
     */
    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : nameCase.apply(attribute.name());
    }

    /**
     * Converts a database value back to the corresponding enum constant.
     * <p>
     * Reading stays case-insensitive on purpose, so that legacy rows persisted in lower case
     * (or by migrations) keep resolving after switching a column to {@link Case#UPPER}.
     *
     * @param dbData the value read from the database (may be {@code null})
     * @return the resolved enum constant, or {@code null}
     * @throws IllegalArgumentException if {@code dbData} does not match any enum constant (case-insensitive)
     */
    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumClass, dbData.toUpperCase(Locale.ROOT));
    }
}