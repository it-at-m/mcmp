package de.muenchen.mcmp.job;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface JobMapper {

    @Mapping(target = "serverName", source = "server.name")
    JobDTO toDTO(final Job job);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "server", ignore = true)
    @Mapping(target = "appService", ignore = true)
    @Mapping(target = "awxDuration", ignore = true)
    @Mapping(target = "jobDuration", ignore = true)
    Job toEntity(final JobDTO jobDTO);
}
