package de.muenchen.mcmp.loadbalancer;

public interface LbVirtualServerList {
    Long getId();
    String getName();
    String getListen();
    Integer getPort();
    String getAppserviceName();
}
