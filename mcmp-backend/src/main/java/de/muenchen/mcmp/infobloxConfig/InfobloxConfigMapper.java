package de.muenchen.mcmp.infobloxConfig;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InfobloxConfigMapper {

    @Mapping(target = "apiPassword", ignore = true)
    InfobloxConfigDTO toDTO(final InfobloxConfig awxConfig);

    List<InfobloxConfigDTO> toDTOs(final List<InfobloxConfig> awxConfigs);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "apiPasswordEncrypted", ignore = true)
    InfobloxConfig toEntity(final InfobloxConfigDTO awxConfigDTO);
}

