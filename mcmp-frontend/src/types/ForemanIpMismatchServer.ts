import type ForemanIpMismatchIp from "@/types/ForemanIpMismatchIp";

export default interface ForemanIpMismatchServer {
  server_id: number;
  server_name: string;
  power_state: string;
  foreman_host_name: string;
  foreman_fqdn: string;
  ips: ForemanIpMismatchIp[];
}
