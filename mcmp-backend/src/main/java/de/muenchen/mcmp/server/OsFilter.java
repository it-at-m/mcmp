package de.muenchen.mcmp.server;

import lombok.Data;

@Data
public class OsFilter {
    private final boolean linux;
    private final boolean mngLinux;
    private final boolean windows;
    private final boolean mngWindows;
    private final boolean windowsClient;
    private final boolean oracle;
    private final boolean nonOracle;
    private final boolean unmanaged;

    public OsFilter(final String os) {
        this.linux = "linux".equalsIgnoreCase(os);
        this.mngLinux = "mng-linux".equalsIgnoreCase(os);
        this.windows = "windows".equalsIgnoreCase(os);
        this.mngWindows = "mng-windows".equalsIgnoreCase(os);
        this.windowsClient = "windows-client".equalsIgnoreCase(os);
        this.oracle = "oracle".equalsIgnoreCase(os);
        this.nonOracle = "non-oracle".equalsIgnoreCase(os);
        this.unmanaged = "unmanaged".equalsIgnoreCase(os);

    }
}
