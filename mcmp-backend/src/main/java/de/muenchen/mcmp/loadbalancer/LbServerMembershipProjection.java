package de.muenchen.mcmp.loadbalancer;

public interface LbServerMembershipProjection {
    Long getVsId();
    String getVsDomain();
    String getPoolName();
    String getMemberIp();
    Integer getMemberPort();
}
