package de.muenchen.mcmp.baasConfig;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BaasConfigMapper {

    BaasConfigDTO toDTO(final BaasConfig baasConfig);

    List<BaasConfigDTO> toDTOs(final List<BaasConfig> baasConfigs);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BaasConfig toEntity(final BaasConfigDTO baasConfigDTO);
}

