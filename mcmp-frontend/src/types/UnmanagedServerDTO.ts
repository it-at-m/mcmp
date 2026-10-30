/**
 * Mirror of the backend record `de.muenchen.mcmp.server.UnmanagedServerDTO`.
 */
export default interface UnmanagedServerDTO {
  serverID: number;
  serverName: string;
  serverFQDN: string;
  serverPowerState: string;
  serverVMID: string;
  serverOS: string;
  cluster: string;
  cloudName: string;
  vCenterServerGUI: string;
}
