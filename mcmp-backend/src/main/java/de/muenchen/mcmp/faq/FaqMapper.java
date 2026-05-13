package de.muenchen.mcmp.faq;

import de.muenchen.mcmp.faqCategory.FaqCategory;
import de.muenchen.mcmp.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Mapper interface for converting between {@link Faq} entities and {@link FaqDTO}s.
 */
@Mapper(componentModel = "spring")
public interface FaqMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "userId", source = "user.id")
    FaqDTO toDTO(Faq entity);

    List<FaqDTO> toDTOList(List<Faq> entities);

    @Mapping(target = "category", source = "categoryId")
    @Mapping(target = "user", source = "userId")
    Faq toEntity(FaqDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", source = "categoryId")
    @Mapping(target = "user", source = "userId")
    @Mapping(target = "isPublished", source = "isPublished")
    void updateEntityFromDto(FaqDTO dto, @MappingTarget Faq entity);

    /**
     * Maps a category ID to a shell FaqCategory entity.
     * @param categoryId the ID to map
     * @return a category entity with only the ID set
     */
    default FaqCategory mapCategoryIdToCategory(Long categoryId) {
        if (categoryId == null) return null;
        FaqCategory category = new FaqCategory();
        category.setId(categoryId);
        return category;
    }

    /**
     * Maps a user ID to a shell User entity.
     * @param userId the ID to map
     * @return a user entity with only the ID set
     */
    default User mapUserIdToUser(Long userId) {
        if (userId == null) return null;
        User user = new User();
        user.setId(userId);
        return user;
    }
}