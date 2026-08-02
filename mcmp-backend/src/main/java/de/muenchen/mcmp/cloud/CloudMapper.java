package de.muenchen.mcmp.cloud;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CloudMapper {

    @Mapping(target = "apiPassword", ignore = true)
    CloudDTO toDTO(final Cloud cloud);

    List<CloudDTO> toDTOs(final List<Cloud> clouds);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "apiPasswordEncrypted", ignore = true)
    @ValueMappings({
            @ValueMapping(target = "VMWARE", source = "VMWARE"),
            @ValueMapping(target = "PROXMOX", source = "PROXMOX"),
            @ValueMapping(target = "UCS_MANAGER", source = "UCS_MANAGER"),
            @ValueMapping(target = "UCS_CIMC", source = "UCS_CIMC")
    })
    Cloud toEntity(final CloudDTO cloudDTO);
}

