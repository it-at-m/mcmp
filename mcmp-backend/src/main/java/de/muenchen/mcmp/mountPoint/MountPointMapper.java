package de.muenchen.mcmp.mountPoint;

import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface MountPointMapper {
    MountPointDTO toDTO(MountPoint mountPoint);
    List<MountPointDTO> toDTOs(final List<MountPoint> mountPoints);
}

