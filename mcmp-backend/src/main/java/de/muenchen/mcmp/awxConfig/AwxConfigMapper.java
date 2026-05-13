package de.muenchen.mcmp.awxConfig;

import de.muenchen.mcmp.infobloxConfig.InfobloxConfig;
import de.muenchen.mcmp.infobloxConfig.InfobloxConfigDTO;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AwxConfigMapper {

    @Named("toDTO")
    @Mapping(target = "apiPassword", ignore = true)
    AwxConfigDTO toDTO(final AwxConfig awxConfig);

    @Mapping(target = "apiPassword", source = "apiPasswordEncrypted")
    AwxConfigDTO toDTODecryptedPassword(final AwxConfig awxConfig);

    @IterableMapping(qualifiedByName = "toDTO")
    List<AwxConfigDTO> toDTOs(final List<AwxConfig> awxConfigs);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "apiPasswordEncrypted", ignore = true)
    AwxConfig toEntity(final AwxConfigDTO awxConfigDTO);

    default String map(byte[] value) {
        return value == null ? null : new String(value);
    }
}

