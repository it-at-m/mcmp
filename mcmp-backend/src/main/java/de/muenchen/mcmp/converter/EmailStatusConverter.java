package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.EmailStatus;
import jakarta.persistence.Converter;

/**
 * A JPA attribute converter that maps the {@link EmailStatus} enum to its string representation in the database
 * and converts string values from the database back to the corresponding {@link EmailStatus} enum.
 *
 * This converter is automatically applied due to the {@code autoApply = true} attribute.
 * The conversion to the database column stores the enum values as lowercase strings.
 * For instance, {@code EmailStatus.NEW} is stored as {@code "new"} in the database.
 * Conversely, database strings are converted back to their respective {@link EmailStatus} values
 * by mapping them to the corresponding enumerations in a case-insensitive manner.
 */
@Converter(autoApply = true)
public class EmailStatusConverter extends BaseEnumConverter<EmailStatus> {

    public EmailStatusConverter() {
        super(EmailStatus.class);
    }
}