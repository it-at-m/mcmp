package de.muenchen.mcmp.action;

import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface ActionMapper {
    ActionDTO toDTO(final Action action);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @ValueMappings({
            @ValueMapping(target = "template", source = "template"),
            @ValueMapping(target = "workflow", source = "workflow")
    })
    Action toEntity(final ActionDTO actionDTO);

}