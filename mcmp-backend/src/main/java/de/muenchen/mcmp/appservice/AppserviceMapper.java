package de.muenchen.mcmp.appservice;

import de.muenchen.mcmp.server.ServerListExtendedDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AppserviceMapper {

    @Mapping(target = "ownedByUsername", source = "ownedBy.username")
    @Mapping(target = "ownedByName", source = "ownedBy.name")
    @Mapping(target = "serviceOwnerDelegateUsername", source = "serviceOwnerDelegate.username")
    @Mapping(target = "serviceOwnerDelegateName", source = "serviceOwnerDelegate.name")
    @Mapping(target = "changeGroupName", source = "changeGroup.name")
    @Mapping(target = "changeGroupSysId", source = "changeGroup.sysId")

    AppserviceDTO toDto(Appservice appservice);

    @Mapping(target = "ownedBy", ignore = true)
    @Mapping(target = "serviceOwnerDelegate", ignore = true)
    Appservice toEntity(AppserviceDTO appserviceDTO);

    @Mapping(target = "ownedByUsername", source = "appservice.ownedBy.username")
    @Mapping(target = "ownedByName", source = "appservice.ownedBy.name")
    @Mapping(target = "serviceOwnerDelegateUsername", source = "appservice.serviceOwnerDelegate.username")
    @Mapping(target = "serviceOwnerDelegateName", source = "appservice.serviceOwnerDelegate.name")
    @Mapping(target = "changeGroupName", source = "appservice.changeGroup.name")
    @Mapping(target = "changeGroupSysId", source = "appservice.changeGroup.sysId")
    @Mapping(target = "servers", source = "servers")
    AppserviceDTO toDtoWithServers(Appservice appservice, List<ServerListExtendedDTO> servers);
}
