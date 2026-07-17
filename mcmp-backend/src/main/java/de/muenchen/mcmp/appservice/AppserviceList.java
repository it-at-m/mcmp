package de.muenchen.mcmp.appservice;

import de.muenchen.mcmp.types.EnvironmentType;

public interface AppserviceList {
    Long getId();
    String getName();
    Boolean getHasServers();
    EnvironmentType getEnvironment();
    Boolean getIsFavorite();
}

