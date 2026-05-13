package de.muenchen.mcmp.server;

import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerType;

public interface ServerList {
    Long getId();
    String getName();
    String getPowerState();
    String getOS();
    ServerKind getServerKind();
    ServerType getServerType();
    Boolean getHasRightsizingRecommendations();
}

