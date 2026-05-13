package de.muenchen.mcmp.job;

import de.muenchen.mcmp.types.DbType;

import java.util.Locale;
import java.util.Optional;

/**
 * Utility for mapping user- or config-provided database type strings to {@link DbType}.
 * <p>
 * The mapping is case-insensitive and ignores leading/trailing whitespace. Unknown or empty inputs
 * are handled gracefully:
 * <ul>
 *   <li>{@link #parse(String)} returns {@link Optional#empty()} for {@code null}, blank, or unknown values</li>
 *   <li>{@link #normalizedNameOrUnknown(String)} returns {@link #UNKNOWN} for {@code null}, blank, or unknown values</li>
 * </ul>
 */
public final class DbTypeMapper {

    /**
     * Fallback value returned by {@link #normalizedNameOrUnknown(String)} when the input cannot be mapped.
     */
    public static final String UNKNOWN = "unknown";

    private DbTypeMapper() {}

    /**
     * Parses the given database type string into a {@link DbType}.
     * <p>
     * Matching is performed on {@code dbType.trim().toLowerCase(Locale.ROOT)}.
     *
     * @param dbType raw database type identifier (e.g. {@code "postgres"}, {@code "OracleDB"}, {@code "mssql"})
     * @return an {@link Optional} containing the resolved {@link DbType}, or {@link Optional#empty()} if the input is
     * {@code null}, blank, or not recognized
     */
    public static Optional<DbType> parse(final String dbType) {
        if (dbType == null || dbType.isBlank()) {
            return Optional.empty();
        }

        final String key = dbType.trim().toLowerCase(Locale.ROOT);

        return Optional.ofNullable(switch (key) {
            case "oracle", "oracledb" -> DbType.ORACLE;
            case "maria", "mariadb" -> DbType.MARIADB;
            case "mssql", "mssqldb", "sqlserver" -> DbType.MSSQL;
            case "postgresql", "postgres", "pg" -> DbType.POSTGRESQL;
            case "mysql", "mysqldb" -> DbType.MYSQL;
            case "mongodb", "mongo" -> DbType.MONGODB;
            case "adabas", "adabase" -> DbType.ADABAS;
            default -> null;
        });
    }

    /**
     * Returns the normalized database name for the given input, or {@link #UNKNOWN} if the input is not recognized.
     *
     * @param dbType raw database type identifier
     * @return {@link DbType#normalizedName()} if resolvable; otherwise {@link #UNKNOWN}
     */
    public static String normalizedNameOrUnknown(final String dbType) {
        return parse(dbType)
                .map(DbType::normalizedName)
                .orElse(UNKNOWN);
    }
}