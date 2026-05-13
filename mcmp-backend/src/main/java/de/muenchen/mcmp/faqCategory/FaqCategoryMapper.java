package de.muenchen.mcmp.faqCategory;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Mapper interface for converting between {@link FaqCategory} entities and {@link FaqCategoryDTO}s.
 * Uses MapStruct for implementation.
 */
@Mapper(componentModel = "spring")
public interface FaqCategoryMapper {

    /**
     * Maps an entity to its DTO.
     *
     * @param entity the source entity
     * @return the mapped DTO
     */
    FaqCategoryDTO toDTO(FaqCategory entity);

    /**
     * Maps a list of entities to a list of DTOs.
     *
     * @param entities the source entities
     * @return the mapped list of DTOs
     */
    List<FaqCategoryDTO> toDTOList(List<FaqCategory> entities);

    /**
     * Maps a DTO to its entity.
     *
     * @param dto the source DTO
     * @return the mapped entity
     */
    FaqCategory toEntity(FaqCategoryDTO dto);

    /**
     * Updates an existing entity from DTO data.
     *
     * @param dto    the source DTO containing new values
     * @param entity the target entity to update
     */
    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(FaqCategoryDTO dto, @MappingTarget FaqCategory entity);
}