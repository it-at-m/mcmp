package de.muenchen.mcmp.nic;

import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface NicMapper {
    NicDTO toDTO(final Nic nic);
    List<NicDTO> toDTOs(final List<Nic> nics);
}

