package de.muenchen.mcmp.snowConfig;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SnowConfigMapper {

    @Mapping(target = "apiClientSecret", ignore = true)
    SnowConfigDTO toDTO(final SnowConfig SnowConfig);

    List<SnowConfigDTO> toDTOs(final List<SnowConfig> SnowConfigs);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "apiClientSecretEncrypted", ignore = true)
    SnowConfig toEntity(final SnowConfigDTO snowConfigDTO);
}

