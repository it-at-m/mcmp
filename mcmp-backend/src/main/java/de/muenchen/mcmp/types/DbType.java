package de.muenchen.mcmp.types;

public enum DbType {
    POSTGRESQL("PostgreSQL"),
    MARIADB("MariaDB"),
    ORACLE("OracleDB"),
    MSSQL("MSSQL"),
    MYSQL("MySQL"),
    MONGODB("MongoDB"),
    ADABAS("Adabas");

    private final String normalizedName;

    DbType(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String normalizedName() {
        return normalizedName;
    }
}
