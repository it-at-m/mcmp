package de.muenchen.mcmp.job;

import de.muenchen.mcmp.action.Action;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ActionToJobMapper {

    /**
     * Updates a {@code Job} instance with fields taken from an {@code Action}.
     *
     * <p>This mapping is intended to:
     * <ul>
     *   <li>Copy the user-facing identifiers and texts (title/description/execution/success/error details).</li>
     *   <li>Leave persistence-managed fields untouched (e.g., id, version, timestamps).</li>
     *   <li>Perform an in-place update on the given {@code job} (MapStruct {@code @MappingTarget}).</li>
     * </ul>
     *
     * <p><b>Important:</b> The target instance is mutated; callers should ensure they pass an entity/DTO
     * that is safe to update within the current transaction/context.
     *
     * @param action the source action defining identifiers and descriptive texts
     * @param job the target job to be updated in place
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "actionIdentifier", source = "identifier")
    @Mapping(target = "actionTitle", source = "title")
    @Mapping(target = "actionDescription", source = "description")
    @Mapping(target = "actionErrorTitle", source = "errorTitle")
    @Mapping(target = "actionErrorDescription", source = "errorDescription")
    @Mapping(target = "actionSuccessTitle", source = "successTitle")
    @Mapping(target = "actionSuccessDescription", source = "successDescription")
    @Mapping(target = "actionExecutionTitle", source = "executionTitle")
    @Mapping(target = "actionExecutionDescription", source = "executionDescription")
    void updateJobFromAction(Action action, @MappingTarget Job job);
}
