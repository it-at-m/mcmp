package de.muenchen.mcmp.backup;

import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface BackupMapper {
    BackupDTO toDTO(final Backup backup);
    List<BackupDTO> toDTOs(final List<Backup> backups);
}

