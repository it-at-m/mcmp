package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.DbType;
import jakarta.persistence.Converter;

/**
 * Auto-applied JPA converter for {@link DbType}.
 */
@Converter(autoApply = true)
public class DbTypeConverter extends BaseEnumConverter<DbType> {

    public DbTypeConverter() {
        super(DbType.class);
    }
}