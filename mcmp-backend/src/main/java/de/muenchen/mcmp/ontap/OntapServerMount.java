package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.server.Server;

import java.util.List;

/**
 * Common interface for ONTAP server mounts (qtree and volume).
 */
public interface OntapServerMount {

    Long getId();

    String getMountPoint();

    void setMountPoint(String mountPoint);

    String getFilesystem();

    void setFilesystem(String filesystem);

    List<String> getOptions();

    void setOptions(List<String> options);

    Server getServer();

    void setServer(Server server);

    AbstractEntity getOntapEntity();
}