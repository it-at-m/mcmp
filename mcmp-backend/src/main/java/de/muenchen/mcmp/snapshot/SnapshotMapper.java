package de.muenchen.mcmp.snapshot;

import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SnapshotMapper {
    SnapshotDTO toDTO(final Snapshot snapshot);
    List<SnapshotDTO> toDTOs(final List<Snapshot> snapshots);
}

