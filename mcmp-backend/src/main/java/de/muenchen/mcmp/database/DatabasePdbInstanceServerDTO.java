package de.muenchen.mcmp.database;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface DatabasePdbInstanceServerDTO {

    String getFqdn();

    String getPdb();

    @JsonIgnore
    Long getPdbInstanceId();
}