package de.muenchen.mcmp.repository;

public interface RepositoryListProjection {
    Long getId();
    String getName();
    String getLockStatus();
    Boolean getIsFavorite();
    Boolean getCanEdit();
}
