package de.muenchen.mcmp.repository;

import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface RepositoryMapper {
    RepositoryDTO toDTO(final Repository repository);
    List<RepositoryDTO> toDTOs(final List<Repository> repositories);
}
