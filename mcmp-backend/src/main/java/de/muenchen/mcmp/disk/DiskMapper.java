package de.muenchen.mcmp.disk;

import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface DiskMapper {
    DiskDTO toDTO(Disk disk);
    List<DiskDTO> toDTOs(final List<Disk> disks);
}

