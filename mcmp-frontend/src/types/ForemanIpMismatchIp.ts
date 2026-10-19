export default interface ForemanIpMismatchIp {
  server_ip: string | null;
  foreman_ip: string | null;
  status: ForemanIpMismatchStatus;
  server_if: string | null;
  foreman_if: string | null;
  server_network: string | null;
  foreman_network: string | null;
}

export type ForemanIpMismatchStatus =
  | "nur_in_MCMP"
  | "nur_in_Foreman"
  | "kein_nic"
  | "keine_IPv4"
  | (string & Record<never, never>);
