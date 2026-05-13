package de.muenchen.mcmp.server;

import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerType;

public interface ServerListExtended {
    Long getId();
    String getName();
    String getPowerState();
    String getOS();
    String getAppserviceNames();
    Integer getNumCpu();
    Integer getMemoryMb();
    Long getVdisksCapacityInBytes();
    ServerKind getServerKind();
    ServerType getServerType();
    Boolean getManaged();
    Boolean getCanEdit();
}
