package de.muenchen.mcmp.network;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NetworkMapper {
    NetworkDTO toDTO(Network network);

    Network toEntity(NetworkDTO networkDTO);

    NetworkGroupDTO toGroupDTO(NetworkGroup networkGroup);

    NetworkGroup toGroupEntity(NetworkGroupDTO networkGroupDTO);

    List<NetworkGroupDTO> toDTos(List<NetworkGroup> networkGroup);
}
