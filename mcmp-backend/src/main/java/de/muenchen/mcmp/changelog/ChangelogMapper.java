package de.muenchen.mcmp.changelog;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Mapper interface for converting between {@link Changelog} entities and {@link ChangelogDTO}s.
 * Uses MapStruct for implementation.
 */
@Mapper(componentModel = "spring")
public interface ChangelogMapper {

    /**
     * Maps a Changelog entity to its DTO.
     * Extracts the author's name from the associated User entity.
     *
     * @param changelog the source entity
     * @return the mapped DTO
     */
    @Mapping(target = "authorName", source = "user.username")
    ChangelogDTO toDTO(Changelog changelog);

    /**
     * Maps a ChangelogDTO to its entity.
     * Note: The user relationship must be handled separately.
     *
     * @param dto the source DTO
     * @return the mapped entity
     */
    @Mapping(target = "user", ignore = true)
    Changelog toEntity(ChangelogDTO dto);

    /**
     * Updates an existing Changelog entity from DTO data.
     *
     * @param dto    the source DTO containing new values
     * @param entity the target entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "isPublished", source = "isPublished")
    void updateEntityFromDto(ChangelogDTO dto, @MappingTarget Changelog entity);

    /**
     * Utility method to map {@link Date} to {@link OffsetDateTime}.
     * Uses the system default time zone.
     *
     * @param value the Date to convert
     * @return the converted OffsetDateTime or null
     */
    default OffsetDateTime map(Date value) {
        if (value == null) {
            return null;
        }
        return value.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /**
     * Utility method to map {@link OffsetDateTime} to {@link Date}.
     * Uses the system default time zone.
     *
     * @param value the OffsetDateTime to convert
     * @return the converted Date or null
     */
    default Date map(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.toInstant());
    }
}